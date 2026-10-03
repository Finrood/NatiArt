BEGIN;
LOCK TABLE product_images IN ACCESS EXCLUSIVE MODE;
ALTER TABLE product_images ADD COLUMN IF NOT EXISTS image_position integer;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM product_images GROUP BY product_id
    HAVING count(image_position) > 0 AND count(image_position) < count(*)) THEN
    RAISE EXCEPTION 'Partially populated image positions require manual reconciliation';
  END IF;
END $$;
WITH ranked AS (
  SELECT ctid, (row_number() OVER (PARTITION BY product_id ORDER BY images, ctid) - 1)::integer AS position
  FROM product_images WHERE image_position IS NULL
)
UPDATE product_images AS target SET image_position = ranked.position
FROM ranked WHERE target.ctid = ranked.ctid;
ALTER TABLE product_images ALTER COLUMN image_position SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS product_images_position_unique ON product_images(product_id, image_position);
COMMIT;
