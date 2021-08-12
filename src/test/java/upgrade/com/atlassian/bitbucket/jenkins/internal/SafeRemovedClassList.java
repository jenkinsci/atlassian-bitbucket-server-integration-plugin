package upgrade.com.atlassian.bitbucket.jenkins.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class SafeRemovedClassList {

    public static Map<String, String> loadSafeList() {
        Map<String, String> safeFiles = new HashMap<>();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(SafeRemovedClassList.class.getResourceAsStream("/safeRemovedClass.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    //#are comments
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split(";");
                    if (parts.length != 2) {
                        throw new RuntimeException("Invalid format of safeRemovedClass.txt, format is 'FQN;Reason'");
                    }
                    safeFiles.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return safeFiles;
    }
}
