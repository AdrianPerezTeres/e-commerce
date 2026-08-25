DO $$
BEGIN
  CREATE TABLE IF NOT EXISTS products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(500) NOT NULL,
    sku         VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    category    VARCHAR(200),
    price       DECIMAL(12,2) NOT NULL CHECK (price >= 0),
    stock       INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    weight_kg   DECIMAL(8,3),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );

  CREATE SEQUENCE IF NOT EXISTS order_number_seq START 1;

  CREATE TABLE IF NOT EXISTS orders (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number  VARCHAR(20) NOT NULL UNIQUE DEFAULT 'ECOMM-' || LPAD(nextval('order_number_seq')::text, 4, '0'),
    user_id       VARCHAR(255),
    status        VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'failed', 'cancelled')),
    total         DECIMAL(12,2) NOT NULL CHECK (total >= 0),
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );

  CREATE TABLE IF NOT EXISTS order_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products(id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    unit_price  DECIMAL(12,2) NOT NULL CHECK (unit_price >= 0),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );

  CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
  CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
  CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
  CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

  INSERT INTO products (name, sku, description, category, price, stock, weight_kg, created_by)
  VALUES
    ('Leather Wallet',       'SEED-ACC', 'Genuine leather bifold wallet, RFID blocking',                    'Accessories',     39.99,  180, 0.150, 'seed'),
    ('Essential Oils Set',   'SEED-BEA', 'Set of 6 aromatherapy oils, 10ml each',                           'Beauty',          24.99,  160, 0.120, 'seed'),
    ('Cookbook',              'SEED-BOO', 'Mediterranean diet recipes, hardcover, 300 pages',                 'Books',           27.50,  100, 0.850, 'seed'),
    ('Cotton T-Shirt',       'SEED-CLO', '100% organic cotton, unisex, available in black',                  'Clothing',        14.99, 1000, 0.200, 'seed'),
    ('Wireless Mouse',       'SEED-ELE', 'Ergonomic wireless mouse with USB receiver',                      'Electronics',     29.99,   75, 0.120, 'seed'),
    ('Organic Coffee Beans', 'SEED-FOB', 'Single origin, medium roast, 1kg bag',                             'Food & Beverage', 18.75,  500, 1.000, 'seed'),
    ('Running Shoes',        'SEED-FOO', 'Lightweight running shoes for daily training',                     'Footwear',        89.99,  150, 0.350, 'seed'),
    ('Board Game',           'SEED-GAM', 'Strategic board game for 2-6 players, ages 12+',                   'Games',           39.99,   85, 1.800, 'seed'),
    ('Mystery Box',          'SEED-GIF', 'Surprise assortment of random items from our catalog',             'Gifts',           15.00,   50, 1.000, 'seed'),
    ('Vitamin D3',           'SEED-HEA', 'Vitamin D3 1000IU, 365 tablets, one year supply',                  'Health',          11.99,  300, 0.150, 'seed'),
    ('Standing Desk',        'SEED-HOM', 'Electric height-adjustable desk, 140-160cm range',                 'Home & Office',  449.99,   15, 35.000, 'seed'),
    ('Cutting Board',        'SEED-KIT', 'Large bamboo cutting board with juice groove',                     'Kitchen',         19.99,  280, 1.300, 'seed'),
    ('Camping Tent',         'SEED-OUT', '4-person dome tent, waterproof 3000mm rating',                     'Outdoors',       199.99,   25, 4.500, 'seed'),
    ('Dog Leash',            'SEED-PET', 'Retractable dog leash, 5m, for dogs up to 30kg',                   'Pets',            18.99,  230, 0.250, 'seed'),
    ('Jump Rope',            'SEED-SPO', 'Speed jump rope, adjustable length, ball bearing',                 'Sports',           9.99,  400, 0.200, 'seed'),
    ('Mechanical Pencil',    'SEED-STA', '0.5mm mechanical pencil with full metal body',                     'Stationery',       8.99,  750, 0.030, 'seed'),
    ('Tape Measure',         'SEED-TOO', '5m retractable tape measure, metric and imperial',                 'Tools',            6.99,  500, 0.200, 'seed')
  ON CONFLICT (sku) DO NOTHING;
END $$;
