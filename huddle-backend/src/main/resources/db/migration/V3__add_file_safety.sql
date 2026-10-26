-- Add safety state fields to project_files
ALTER TABLE project_files 
ADD COLUMN safety_state VARCHAR(50) NOT NULL DEFAULT 'PENDING_ASSESSMENT',
ADD COLUMN safety_assessed_at TIMESTAMP,
ADD COLUMN safety_scan_result VARCHAR(500),
ADD COLUMN safety_reviewed_by UUID REFERENCES users(id),
ADD COLUMN safety_reviewed_at TIMESTAMP,
ADD COLUMN safety_review_notes VARCHAR(1000),
ADD COLUMN incomplete_upload BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN upload_started_at TIMESTAMP;

-- Add safety state fields to file_versions
ALTER TABLE file_versions 
ADD COLUMN safety_state VARCHAR(50) NOT NULL DEFAULT 'PENDING_ASSESSMENT',
ADD COLUMN safety_assessed_at TIMESTAMP,
ADD COLUMN safety_scan_result VARCHAR(500),
ADD COLUMN safety_reviewed_by UUID REFERENCES users(id),
ADD COLUMN safety_reviewed_at TIMESTAMP,
ADD COLUMN safety_review_notes VARCHAR(1000);
