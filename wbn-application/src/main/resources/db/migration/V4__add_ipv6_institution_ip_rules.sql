ALTER TABLE IF EXISTS public.institution_ip_rule
    ADD COLUMN IF NOT EXISTS ip_family character varying(8);

ALTER TABLE IF EXISTS public.institution_ip_rule
    ADD COLUMN IF NOT EXISTS ip_from_bytes bytea;

ALTER TABLE IF EXISTS public.institution_ip_rule
    ADD COLUMN IF NOT EXISTS ip_to_bytes bytea;

ALTER TABLE IF EXISTS public.institution_ip_rule
    ADD COLUMN IF NOT EXISTS cidr_prefix integer;

UPDATE public.institution_ip_rule
SET ip_family = 'IPV4',
    ip_from_bytes = decode(lpad(to_hex(ip_from_long), 8, '0'), 'hex'),
    ip_to_bytes = decode(lpad(to_hex(ip_to_long), 8, '0'), 'hex'),
    cidr_prefix = CASE
        WHEN rule_type = 'SINGLE_IP' THEN 32
        WHEN rule_type = 'CIDR' THEN split_part(rule_raw, '/', 2)::integer
        ELSE NULL
    END
WHERE ip_family IS NULL
  AND ip_from_long IS NOT NULL
  AND ip_to_long IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ip_rule_family
    ON public.institution_ip_rule(institution_id, ip_family);
