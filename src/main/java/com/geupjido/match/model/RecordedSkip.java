package com.geupjido.match.model;

import java.time.Instant;

public record RecordedSkip(int roundNumber, Instant skippedAt) {
}
