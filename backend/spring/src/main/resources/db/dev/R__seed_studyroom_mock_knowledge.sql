-- Kept under the original repeatable migration name to clean up existing development databases.
-- Actual PPT evidence is imported from KNOWLEDGE_DIR by KnowledgeBootstrapRunner.
DELETE FROM knowledge_chunks WHERE source_path LIKE 'dev-mock/%';
