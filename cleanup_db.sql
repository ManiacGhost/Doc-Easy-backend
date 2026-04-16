-- Clean up all test data before adding authentication
-- Run this in pgAdmin or any PostgreSQL client

-- Delete all QA records first (foreign key dependency)
DELETE FROM qa_records;

-- Delete all uploaded assets
DELETE FROM uploaded_assets;

-- Optional: Reset sequences to start from 1
ALTER SEQUENCE qa_records_id_seq RESTART WITH 1;
ALTER SEQUENCE uploaded_assets_id_seq RESTART WITH 1;

-- Verify cleanup
SELECT COUNT(*) as qa_records_count FROM qa_records;
SELECT COUNT(*) as uploaded_assets_count FROM uploaded_assets;

