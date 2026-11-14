import parser.SqlEngine;
import storage.StorageRepository;
import java.util.Scanner;

/**
 * Главный класс программы: запускает консольный интерфейс школьной СУБД.
 */
public class Main {
    public static void main(String[] args) {
        // Создаем хранилище, которое будет сохранять файлы в папку "db_data"
        StorageRepository repository = new StorageRepository("db_data");
        // Запускаем движок СУБД (он сам загрузит старые таблицы с диска, если они есть)
        SqlEngine engine = new SqlEngine(repository);
        // Создаем сканер для чтения ввода пользователя из консоли
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Школьная СУБД на Java (v1.0) ===");
        System.out.println("Команды: SQL-запрос | DEMO (создать школьную БД) | BENCHMARK (тест скорости) | EXIT (выход)");

        // Бесконечный цикл приема команд от пользователя
        while (true) {
            System.out.print("\nsql> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("EXIT")) {
                System.out.println("Завершение работы. Данные сохранены в папку db_data.");
                break;
            } else if (input.equalsIgnoreCase("DEMO")) {
                runSchoolDemo(engine);
            } else if (input.equalsIgnoreCase("BENCHMARK")) {
                runBenchmark(engine);
            } else {
                engine.execute(input);
            }
        }
    }

    // Метод создает 3 связанные таблицы школы и заполняет их тестовыми данными
    private static void runSchoolDemo(SqlEngine engine) {
        System.out.println("--- 1. Создание связанных таблиц школы ---");
        // Таблица учеников с личной информацией
        engine.execute("CREATE TABLE students (id INT PK, full_name STRING, birth_date DATE, address STRING, email STRING, is_active BOOLEAN);");
        // Таблица школьных предметов
        engine.execute("CREATE TABLE subjects (id INT PK, title STRING, teacher STRING);");
        // Таблица оценок, связанная внешними ключами (FK) с учениками и предметами
        engine.execute("CREATE TABLE grades (id INT PK, student_id INT FK students(id), subject_id INT FK subjects(id), score DOUBLE, exam_date DATE);");

        System.out.println("\n--- 2. Заполнение данными ---");
        // Множественная вставка двух учеников одним запросом
        engine.execute("INSERT INTO students VALUES (1, 'Иванов Иван', '2008-04-15', 'ул. Ленина, д. 10, кв. 5', 'ivanov@school.ru', TRUE), (2, 'Петрова Анна', '2008-09-22', 'пр. Гагарина, д. 42', 'petrova@school.ru', TRUE);");
        // Частичная вставка (указываем только часть столбцов, остальные станут null)
        engine.execute("INSERT INTO students (id, full_name, email, is_active) VALUES (3, 'Сидоров Максим', 'sidorov@school.ru', FALSE);");

        // Заполняем предметы
        engine.execute("INSERT INTO subjects VALUES (10, 'Математика', 'Смирнова Е.В.'), (20, 'Информатика', 'Козлов А.И.');");

        // Выставляем оценки ученикам
        engine.execute("INSERT INTO grades VALUES (101, 1, 10, 4.8, '2026-10-15');");
        engine.execute("INSERT INTO grades VALUES (102, 1, 20, 5.0, '2026-10-20');");
        engine.execute("INSERT INTO grades VALUES (103, 2, 10, 4.2, '2026-10-15');");

        System.out.println("\n--- 3. Вывод всех таблиц ---");
        engine.execute("SELECT * FROM students;");
        engine.execute("SELECT * FROM subjects;");
        engine.execute("SELECT * FROM grades;");
    }


    // Метод генерирует 10 000 записей одним пакетным запросом и сравнивает скорость поиска
    private static void runBenchmark(SqlEngine engine) {
        System.out.println("--- Тест производительности на 10 000 записей учеников ---");
        engine.execute("DROP TABLE bench_students;");
        engine.execute("CREATE TABLE bench_students (id INT PK, full_name STRING, email STRING, avg_score DOUBLE);");

        // Собираем 10 000 записей в один пакетный INSERT, чтобы не перезаписывать диск 10 000 раз
        StringBuilder batchInsert = new StringBuilder("INSERT INTO bench_students VALUES ");
        for (int i = 1; i <= 10000; i++) {
            batchInsert.append("(")
                    .append(i).append(", ")
                    .append("'Ученик_").append(i).append("', ")
                    .append("'student").append(i).append("@school.ru', ")
                    .append(3.0 + (i % 20) * 0.1)
                    .append(")");
            if (i < 10000) {
                batchInsert.append(", ");
            }
        }
        batchInsert.append(";");
        engine.execute(batchInsert.toString());

        System.out.println("\n1. Поиск ученика по email БЕЗ индекса:");
        engine.execute("SELECT * FROM bench_students WHERE email = 'student9876@school.ru';");

        System.out.println("\n2. Создание индекса и поиск С индексом:");
        engine.execute("CREATE INDEX idx_student_email ON bench_students (email);");
        engine.execute("SELECT * FROM bench_students WHERE email = 'student9876@school.ru';");
    }

}
