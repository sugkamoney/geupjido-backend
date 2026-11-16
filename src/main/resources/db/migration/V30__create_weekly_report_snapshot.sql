-- 일요일 18시 마감 집계와 21시 공개를 분리한다. 공개 전 행은 초안이다.
CREATE TABLE weekly_report
(
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_start_at          TIMESTAMPTZ NOT NULL,
    period_end_at            TIMESTAMPTZ NOT NULL,
    cutoff_completed_at      TIMESTAMPTZ NOT NULL,
    published_at             TIMESTAMPTZ,
    period_valid_vote_count  BIGINT,
    period_participant_count BIGINT,
    changed_grade_zone_count INTEGER,
    summary_text             TEXT,

    CONSTRAINT uq_weekly_report_period_end UNIQUE (period_end_at),
    CONSTRAINT chk_weekly_report_seven_days
        CHECK (period_end_at = period_start_at + INTERVAL '7 days'),
    CONSTRAINT chk_weekly_report_sunday_cutoff
        CHECK (EXTRACT(ISODOW FROM period_end_at AT TIME ZONE 'Asia/Seoul') = 7
            AND (period_end_at AT TIME ZONE 'Asia/Seoul')::time = TIME '18:00'),
    CONSTRAINT chk_weekly_report_cutoff_completed
        CHECK (cutoff_completed_at >= period_end_at),
    CONSTRAINT chk_weekly_report_publication_order
        CHECK (published_at IS NULL OR published_at >= cutoff_completed_at),
    CONSTRAINT chk_weekly_report_release_time
        CHECK (published_at IS NULL OR published_at >= period_end_at + INTERVAL '3 hours'),
    CONSTRAINT chk_weekly_report_period_counts
        CHECK ((period_valid_vote_count IS NULL AND period_participant_count IS NULL)
            OR (period_valid_vote_count IS NOT NULL
                AND period_participant_count IS NOT NULL
                AND period_valid_vote_count >= 0 AND period_participant_count >= 0
                AND period_participant_count <= period_valid_vote_count)),
    CONSTRAINT chk_weekly_report_published_counts
        CHECK (published_at IS NULL OR period_valid_vote_count IS NOT NULL),
    CONSTRAINT chk_weekly_report_grade_count
        CHECK (changed_grade_zone_count IS NULL OR changed_grade_zone_count >= 0)
);

-- 권역·구의 현재 행이 바뀌어도 과거 리포트의 이름·급지·진행 수치를 유지한다.
-- zone_id와 city_id는 화면 이동용 식별자이며 의도적으로 현재 마스터를 참조하지 않는다.
CREATE TABLE weekly_report_zone_snapshot
(
    report_id             BIGINT       NOT NULL REFERENCES weekly_report (id),
    zone_id               VARCHAR(50)  NOT NULL,
    zone_name             VARCHAR(50)  NOT NULL,
    city_id               VARCHAR(10)  NOT NULL,
    city_name             VARCHAR(50)  NOT NULL,
    region_code           VARCHAR(20)  NOT NULL,
    region_name           VARCHAR(20)  NOT NULL,
    ranking_basis         VARCHAR(10)  NOT NULL,
    vote_tier             NUMERIC(2, 1),
    valid_vote_count      INTEGER      NOT NULL,
    participant_count     INTEGER      NOT NULL,
    cross_zone_vote_count INTEGER      NOT NULL,

    CONSTRAINT pk_weekly_report_zone_snapshot PRIMARY KEY (report_id, zone_id),
    CONSTRAINT chk_weekly_report_zone_basis
        CHECK ((ranking_basis = 'VOTE' AND vote_tier IS NOT NULL
                AND vote_tier BETWEEN 1.0 AND 5.9)
            OR (ranking_basis = 'INITIAL' AND vote_tier IS NULL)),
    CONSTRAINT chk_weekly_report_zone_counts
        CHECK (valid_vote_count >= 0 AND participant_count >= 0
            AND cross_zone_vote_count >= 0
            AND participant_count <= valid_vote_count
            AND cross_zone_vote_count <= valid_vote_count)
);

CREATE INDEX idx_weekly_report_published
    ON weekly_report (period_end_at DESC) WHERE published_at IS NOT NULL;

-- 초안 트랜잭션이 실패해 롤백되더라도 첫 리포트의 발행 지연 상태를 구분한다.
CREATE TABLE weekly_report_attempt_failure
(
    period_end_at TIMESTAMPTZ PRIMARY KEY,
    failed_at     TIMESTAMPTZ NOT NULL
);

-- 공개된 보고서와 그 권역 스냅샷은 이후 재집계로 수정할 수 없다.
CREATE FUNCTION protect_published_weekly_report() RETURNS trigger AS $$
BEGIN
    IF OLD.published_at IS NOT NULL THEN
        RAISE EXCEPTION '이미 발행된 주간 리포트는 변경할 수 없습니다.'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_published_weekly_report
    BEFORE UPDATE OR DELETE ON weekly_report
    FOR EACH ROW EXECUTE FUNCTION protect_published_weekly_report();

CREATE FUNCTION protect_published_weekly_report_zone() RETURNS trigger AS $$
DECLARE
    target_report_id BIGINT;
    target_is_published BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        target_report_id := NEW.report_id;
    ELSE
        target_report_id := OLD.report_id;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.report_id <> OLD.report_id THEN
            RAISE EXCEPTION '권역 스냅샷의 리포트는 변경할 수 없습니다.'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT published_at IS NOT NULL INTO target_is_published
    FROM weekly_report WHERE id = target_report_id;
    IF target_is_published THEN
        RAISE EXCEPTION '발행된 리포트의 권역 스냅샷은 변경할 수 없습니다.'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_published_weekly_report_zone
    BEFORE INSERT OR UPDATE OR DELETE ON weekly_report_zone_snapshot
    FOR EACH ROW EXECUTE FUNCTION protect_published_weekly_report_zone();
