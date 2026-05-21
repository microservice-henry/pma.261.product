CREATE TABLE IF NOT EXISTS products.product (
    id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(255) NOT NULL,
    price   NUMERIC(10, 2) NOT NULL,
    unit    VARCHAR(50)  NOT NULL
);
