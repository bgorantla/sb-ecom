-- Empties the store: products, categories, carts, orders, payments and addresses,
-- plus every user except "admin" and "demo-admin" (so your admin password is kept).
-- Used by demo-setup.sh when you choose to start from an empty store.

SET client_min_messages = warning;  -- skip the "truncate cascades to..." notices

-- Empty every table except the three that hold users and their roles.
-- CASCADE also empties tables that point at these (e.g. order items).
DO $$
DECLARE t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
             WHERE schemaname = 'public' AND tablename NOT IN ('users', 'roles', 'user_role')
    LOOP
        EXECUTE format('TRUNCATE TABLE %I CASCADE', t);
    END LOOP;
END $$;

DELETE FROM user_role WHERE user_id IN (SELECT user_id FROM users WHERE username NOT IN ('admin', 'demo-admin'));
DELETE FROM users WHERE username NOT IN ('admin', 'demo-admin');
