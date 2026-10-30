ALTER TABLE complex
    ADD COLUMN price_source VARCHAR(40);

UPDATE complex
SET price_source = 'APARTMENT_SALE'
WHERE price IS NOT NULL;

ALTER TABLE complex
    ADD CONSTRAINT chk_complex_price_source
        CHECK (price_source IS NULL OR price_source IN (
            'APARTMENT_SALE', 'PRESALE_OR_OCCUPANCY_RIGHT'
        )),
    ADD CONSTRAINT chk_complex_price_source_consistency
        CHECK ((price IS NULL) = (price_source IS NULL));
