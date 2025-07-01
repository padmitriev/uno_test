import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

// v1.2
public class Main {
    private static final int MAX_MEMORY_USAGE_MB = 900;
    private static final int CHECK_MEMORY_EVERY = 10000;
    private static final Path TEMP_FILE = Paths.get("temp_lines.dat");

    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("Usage: java -jar uno_groups-1.0.jar <input_file>");
            return;
        }

        long startTime = System.currentTimeMillis();
        String inputFile = args[0];
        Path outputFile = Path.of("groups.txt");

        try {
            // 1. Первый проход: индексация уникальных строк
            int totalLines = indexLines(inputFile);

            // 2. Второй проход: построение связей
            DSU dsu = buildConnections(totalLines);

            // 3. Третий проход: подсчет и запись групп
            int groupCount = writeResults(outputFile, dsu, totalLines);

            System.out.println("Groups count: " + groupCount);
            System.out.println("Time: " + (System.currentTimeMillis() - startTime) + " ms");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(TEMP_FILE);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temp file");
            }
        }
    }

    private static int indexLines(String inputFile) throws IOException {
        Set<String> uniqueLines = new HashSet<>();
        try (BufferedWriter writer = Files.newBufferedWriter(TEMP_FILE);
             BufferedReader reader = Files.newBufferedReader(Paths.get(inputFile))) {

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (isValidLine(line) && uniqueLines.add(line)) {
                    writer.write(line);
                    writer.newLine();
                    count++;

                    if (count % CHECK_MEMORY_EVERY == 0) {
                        checkMemory();
                    }
                }
            }
            return count;
        }
    }

    private static DSU buildConnections(int totalLines) throws IOException {
        DSU dsu = new DSU(totalLines);
        try (BufferedReader reader = Files.newBufferedReader(TEMP_FILE)) {
            // Карта для хранения первых вхождений значений
            Map<String, Integer> valueMap = new HashMap<>();

            String line;
            int lineIndex = 0;
            while ((line = reader.readLine()) != null) {
                String[] cols = parseColumns(line);

                for (int pos = 0; pos < cols.length; pos++) {
                    if (!cols[pos].isEmpty()) {
                        String key = pos + ":" + cols[pos];
                        Integer firstIndex = valueMap.get(key);

                        if (firstIndex != null) {
                            dsu.union(lineIndex, firstIndex);
                        } else {
                            valueMap.put(key, lineIndex);
                        }
                    }
                }

                lineIndex++;
                if (lineIndex % CHECK_MEMORY_EVERY == 0) {
                    checkMemory();
                }
            }
        }
        return dsu;
    }

    private static int writeResults(Path outputFile, DSU dsu, int totalLines) throws IOException {
        // Собираем группы
        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < totalLines; i++) {
            int root = dsu.find(i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        // Фильтруем и сортируем группы
        List<List<Integer>> filteredGroups = groups.values().stream()
                .filter(group -> group.size() > 1)
                .sorted((g1, g2) -> Integer.compare(g2.size(), g1.size()))
                .collect(Collectors.toList());

        // Записываем результаты
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
             BufferedReader reader = Files.newBufferedReader(TEMP_FILE)) {

            List<String> lines = reader.lines().collect(Collectors.toList());

            writer.write("Groups count: " + filteredGroups.size() + "\n\n");
            for (int i = 0; i < filteredGroups.size(); i++) {
                writer.write("Group " + (i + 1) + "\n");
                filteredGroups.get(i).stream()
                        .map(lines::get)
                        .sorted()
                        .forEach(line -> {
                            try {
                                writer.write(line + "\n");
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                writer.write("\n");
            }
        }

        return filteredGroups.size();
    }

    private static void checkMemory() {
        long used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        if (used > MAX_MEMORY_USAGE_MB) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean isValidLine(String line) {
        if (line == null || line.trim().isEmpty()) return false;

        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i-1) != '\\')) {
                inQuotes = !inQuotes;
            }
        }
        return !inQuotes && line.contains(";");
    }

    private static String[] parseColumns(String line) {
        List<String> cols = new ArrayList<>();
        int start = 0;
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') inQuotes = !inQuotes;
            if (line.charAt(i) == ';' && !inQuotes) {
                cols.add(line.substring(start, i).replace("\"", "").trim());
                start = i + 1;
            }
        }
        cols.add(line.substring(start).replace("\"", "").trim());
        return cols.toArray(new String[0]);
    }
}

class DSU {
    private final int[] parent;
    private final int[] rank;

    public DSU(int size) {
        parent = new int[size];
        rank = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }
    }

    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);
        }
        return parent[x];
    }

    public void union(int x, int y) {
        int rootX = find(x);
        int rootY = find(y);
        if (rootX == rootY) return;

        if (rank[rootX] < rank[rootY]) {
            parent[rootX] = rootY;
        } else if (rank[rootX] > rank[rootY]) {
            parent[rootY] = rootX;
        } else {
            parent[rootY] = rootX;
            rank[rootX]++;
        }
    }
}