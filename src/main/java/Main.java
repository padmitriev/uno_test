import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("Usage: java -jar uno_groups-1.0.jar <input_file>");
            return;
        }

        long startTime = System.currentTimeMillis();

        String inputFile = args[0];
        Path outputFile = Path.of("groups.txt");

        try {
            // 1. Загрузка и индексация строк
            List<String> lines = new ArrayList<>();
            Map<String, Integer> lineToIndex = new HashMap<>();
            int index = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isValidLine(line) && !lineToIndex.containsKey(line)) {
                        lineToIndex.put(line, index++);
                        lines.add(line);
                    }
                }
            }

            // 2. Инициализация DSU
            DSU dsu = new DSU(lines.size());
            Map<String, List<Integer>> valueToIndices = new HashMap<>();

            // 3. Построение связей
            for (int i = 0; i < lines.size(); i++) {
                int finalI = i;
                String line = lines.get(i);
                String[] cols = parseColumns(line);

                for (int pos = 0; pos < cols.length; pos++) {
                    if (!cols[pos].isEmpty()) {
                        String key = cols[pos] + ":" + pos;
                        valueToIndices.compute(key, (k, v) -> {
                            if (v == null) {
                                List<Integer> list = new ArrayList<>();
                                list.add(finalI);
                                return list;
                            } else {
                                if (!v.isEmpty()) {
                                    dsu.union(finalI, v.get(0));
                                }
                                v.add(finalI);
                                return v;
                            }
                        });
                    }
                }
            }

            // 4. Группировка результатов
            Map<Integer, Set<String>> groups = new HashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                int root = dsu.find(i);
                groups.computeIfAbsent(root, k -> new TreeSet<>()).add(lines.get(i));
            }

            // 5. Фильтрация и сортировка групп
            List<Set<String>> filteredGroups = groups.values().stream()
                    .filter(g -> g.size() > 1)
                    .sorted((g1, g2) -> Integer.compare(g2.size(), g1.size()))
                    .collect(Collectors.toList());

            // 6. Сохранение результатов
            writeGroups(outputFile, filteredGroups);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("Groups count: " + filteredGroups.size());
            System.out.println("Time: " + duration + " ms");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void writeGroups(Path outputFile, List<Set<String>> groups) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Groups count: ").append(groups.size()).append("\n\n");

        for (int i = 0; i < groups.size(); i++) {
            sb.append("Group ").append(i + 1).append("\n");
            groups.get(i).forEach(line -> sb.append(line).append("\n"));
            sb.append("\n");
        }

        Files.write(outputFile, sb.toString().getBytes());
    }

    private static boolean isValidLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"' && (i == 0 || line.charAt(i-1) != '\\')) {
                inQuotes = !inQuotes;
            }
        }

        if (inQuotes) {
            return false;
        }

        String unquoted = line.replace("\"\"", "");
        long quoteCount = unquoted.chars().filter(c -> c == '"').count();
        if (quoteCount > 0 && quoteCount % 2 != 0) {
            return false;
        }

        return line.contains(";");
    }

    private static String[] parseColumns(String line) {
        List<String> columns = new ArrayList<>();
        int start = 0;
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') inQuotes = !inQuotes;
            if (line.charAt(i) == ';' && !inQuotes) {
                columns.add(line.substring(start, i).replace("\"", "").trim());
                start = i + 1;
            }
        }
        columns.add(line.substring(start).replace("\"", "").trim());
        return columns.toArray(new String[0]);
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