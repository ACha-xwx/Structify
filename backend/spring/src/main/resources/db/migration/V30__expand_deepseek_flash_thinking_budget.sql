-- The previous maximum administrator setting would cap Chat's new 64K thinking budget at 32K.
-- Preserve smaller explicit limits and other providers/models; credentials and quotas are unchanged.
UPDATE model_configurations
SET max_output_tokens = 65536, updated_at = CURRENT_TIMESTAMP
WHERE LOWER(TRIM(provider)) = 'deepseek'
  AND model_name = 'deepseek-flash'
  AND max_output_tokens = 32768;
