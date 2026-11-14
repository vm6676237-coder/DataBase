package storage;

import index.IndexManager;
import model.Table;
import java.io.*;
import java.nio.file.Files;
import java.util.*;


public class StorageRepository {
    private final String dataDir;
    private final File walFile;
    private final File indexFile;


    public StorageRepository(String dataDir) {
        this.dataDir = dataDir;
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.walFile = new File(dir, "wal.log");       // Путь к журналу: db_data/wal.log
        this.indexFile = new File(dir, "indexes.idx"); // Путь к индексам: db_data/indexes.idx
    }


    public void logToWal(String query) {
        // try-with-resources автоматически закроет файл после записи
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(walFile, true))) {
            bw.write(query);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Ошибка записи в WAL-журнал: " + e.getMessage());
        }
    }

    /**
     * Очищает журнал wal.log после того, как данные надежно сохранены в файл таблицы.
     */
    public void clearWal() {
        try {
            new FileWriter(walFile, false).close();
        } catch (IOException e) {
            System.err.println("Ошибка очистки WAL-журнала: " + e.getMessage());
        }
    }

    /**
     * Читает команды из wal.log при запуске программы (если прошлый запуск завершился сбоем).
     */
    public List<String> getUncommittedWalQueries() {
        if (!walFile.exists()) return Collections.emptyList();
        try {
            return Files.readAllLines(walFile.toPath());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    // Сохраняем объект таблицы в бинарный файл вида "students.tbl"
    public void saveTable(Table table) {
        File file = new File(dataDir, table.getName() + ".tbl");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(table);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить таблицу " + table.getName(), e);
        }
    }

    // Удаляем файл таблицы с диска при выполнении DROP TABLE
    public void deleteTableFile(String tableName) {
        File file = new File(dataDir, tableName.toLowerCase() + ".tbl");
        if (file.exists()) {
            file.delete();
        }
    }

    // Загружаем все сохраненные таблицы из папки db_data при старте программы
    public Map<String, Table> loadAllTables() {
        Map<String, Table> tables = new HashMap<>();
        File dir = new File(dataDir);
        // Ищем в папке все файлы, заканчивающиеся на ".tbl"
        File[] files = dir.listFiles((d, name) -> name.endsWith(".tbl"));
        if (files != null) {
            for (File f : files) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                    // Восстанавливаем объект Table из байтов файла
                    Table t = (Table) ois.readObject();
                    // Кладем таблицу в память программы
                    tables.put(t.getName(), t);
                } catch (Exception e) {
                    System.err.println("Ошибка чтения файла " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        return tables;
    }

    // Сохраняем менеджер индексов в файл indexes.idx
    public void saveIndexes(IndexManager indexManager) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(indexFile))) {
            oos.writeObject(indexManager);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить индексы", e);
        }
    }

    // Загружаем индексы с диска при запуске программы
    public IndexManager loadIndexes() {
        if (!indexFile.exists()) return new IndexManager();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(indexFile))) {
            return (IndexManager) ois.readObject();
        } catch (Exception e) {
            return new IndexManager();
        }
    }
}

