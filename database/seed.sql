-- Demo data for a freshly created schema. Run once after schema.sql.
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM clients) OR EXISTS (SELECT 1 FROM funeral_requests) THEN
        RAISE EXCEPTION 'Demo seed requires empty clients and funeral_requests tables';
    END IF;
END $$;

INSERT INTO clients (full_name, phone, email) VALUES
    ('Иванов Алексей Петрович', '+79990000001', 'ivanov@example.com'),
    ('Петрова Мария Сергеевна', '+79990000002', 'petrova@example.com'),
    ('Сидоров Павел Андреевич', '+79990000003', 'sidorov@example.com'),
    ('Кузнецова Анна Игоревна', '+79990000004', NULL),
    ('Смирнов Дмитрий Олегович', '+79990000005', 'smirnov@example.com'),
    ('Орлова Елена Викторовна', '+79990000006', NULL);

INSERT INTO funeral_requests
    (client_id, deceased_full_name, ceremony_date, ceremony_type_id, status_id, price, comment)
SELECT c.id, demo.deceased_name, CURRENT_DATE + demo.day_offset,
       demo.type_id, demo.status_id, demo.price, demo.comment
FROM (VALUES
    ('+79990000001', 'Иванов Пётр Николаевич', 7, 1, 1, 45000.00, 'Демонстрационная новая заявка'),
    ('+79990000001', 'Иванова Ольга Ивановна', -30, 2, 4, 62000.00, 'Церемония проведена'),
    ('+79990000002', 'Петров Сергей Михайлович', 3, 1, 2, 78000.00, 'Дата согласована'),
    ('+79990000002', 'Петрова Нина Александровна', 0, 2, 3, 55000.00, 'Подготовка церемонии'),
    ('+79990000003', 'Сидоров Андрей Павлович', 10, 1, 1, 39000.00, NULL),
    ('+79990000003', 'Сидорова Вера Петровна', -12, 2, 5, 48000.00, 'Отменено клиентом'),
    ('+79990000004', 'Кузнецов Игорь Васильевич', 4, 2, 2, 91000.00, NULL),
    ('+79990000004', 'Кузнецова Галина Семёновна', -7, 1, 4, 120000.00, 'Церемония проведена'),
    ('+79990000005', 'Смирнов Олег Дмитриевич', 1, 1, 3, 67000.00, 'Заказ транспорта'),
    ('+79990000005', 'Смирнова Людмила Алексеевна', 14, 2, 1, 43000.00, NULL),
    ('+79990000006', 'Орлов Виктор Иванович', -2, 1, 5, 50000.00, 'Отмена до церемонии'),
    ('+79990000006', 'Орлова Тамара Петровна', 5, 2, 2, 73000.00, 'Дата согласована')
) AS demo(phone, deceased_name, day_offset, type_id, status_id, price, comment)
JOIN clients c ON c.phone = demo.phone;

COMMIT;
