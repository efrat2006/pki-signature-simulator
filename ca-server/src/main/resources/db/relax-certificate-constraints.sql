-- מיגרציה לאופציה 1: אפשור שמירת תעודה שהונפקה בזרימה עצמית (אימות בשאלות אבטחה),
-- שבה אין מאשר אנושי (approver) ולעיתים אין ארגון (organization).
-- להריץ ידנית מול מסד הנתונים ca_db (PostgreSQL) לפני שמפעילים את זרימת ההנפקה.
--
--   psql -h localhost -U postgres -d ca_db -f relax-certificate-constraints.sql

ALTER TABLE certificates ALTER COLUMN approver_id DROP NOT NULL;
ALTER TABLE certificates ALTER COLUMN org_id     DROP NOT NULL;
