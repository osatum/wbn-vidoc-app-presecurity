CREATE TABLE IF NOT EXISTS public.institution_deletion_confirmation
(
    id uuid NOT NULL,
    institution_id bigint NOT NULL,
    actor_key character varying(255) NOT NULL,
    code_hash character varying(128) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT institution_deletion_confirmation_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_institution_deletion_confirmation_institution
    ON public.institution_deletion_confirmation(institution_id);

CREATE INDEX IF NOT EXISTS idx_institution_deletion_confirmation_expires_at
    ON public.institution_deletion_confirmation(expires_at);
