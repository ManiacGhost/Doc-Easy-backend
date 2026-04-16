-- ChatApp Database Setup Script
-- Execute this script in PostgreSQL to create the required tables

-- Create uploaded_assets table
CREATE TABLE IF NOT EXISTS uploaded_assets (
    id BIGSERIAL PRIMARY KEY,
    original_filename VARCHAR(500) NOT NULL,
    stored_filename VARCHAR(500),
    cloud_url TEXT NOT NULL,
    content_type VARCHAR(100),
    asset_type VARCHAR(50) NOT NULL CHECK (asset_type IN ('IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT')),
    size_bytes BIGINT,
    extracted_text TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create qa_records table
CREATE TABLE IF NOT EXISTS qa_records (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    question VARCHAR(1000),
    answer TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES uploaded_assets(id) ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_uploaded_assets_uploaded_at ON uploaded_assets(uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_uploaded_assets_asset_type ON uploaded_assets(asset_type);
CREATE INDEX IF NOT EXISTS idx_qa_records_asset_id ON qa_records(asset_id);

-- Grant permissions (adjust username as needed)
GRANT ALL PRIVILEGES ON TABLE uploaded_assets TO postgres;
GRANT ALL PRIVILEGES ON TABLE qa_records TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE uploaded_assets_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE qa_records_id_seq TO postgres;

-- Verify tables were created
\dt

-- Show table structure
\d uploaded_assets;
\d qa_records;

