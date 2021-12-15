package upgrade.com.atlassian.bitbucket.jenkins.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class UpgradeTestUtils {

    public static Map<String, String> loadSafelyRemovedClassList() {
        Map<String, String> safeFiles = new HashMap<>();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(UpgradeTestUtils.class.getResourceAsStream("/safeRemovedClass.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    //#are comments
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split(";");
                    if (parts.length != 2) {
                        throw new RuntimeException("Invalid format of safeRemovedClass.txt, format is 'FullyQualifiedClassName;Reason'");
                    }
                    safeFiles.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return safeFiles;
    }
    
    public static Map<String, String> loadSafelyRemovedFieldList() {
        Map<String, String> safeFields = new HashMap<>();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(UpgradeTestUtils.class.getResourceAsStream("/safeRemovedFields.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    //#are comments
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    // Checking for syntax correctness (we don't use the split of class name and field name)
                    String[] definitionCommentParts = line.split(";");
                    if (definitionCommentParts.length == 2 && definitionCommentParts[0].split("#").length == 2) {
                        safeFields.put(definitionCommentParts[0], definitionCommentParts[1]);
                    } else {
                        throw new RuntimeException("Invalid format of safeRemovedFields.txt, format is 'FullyQualifiedClassName#FieldName;Reason'");
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return safeFields;
    }
}
