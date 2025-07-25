package net.oneki.mtac.model.core.util;

public class StringUtils {
    // convert a pascal case string to camel case
    public static String pascalToCamel(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    public static String pascalToSnake(String str) {
        return pascalToSeparator(str, "_");
    }

    public static String pascalToKebab(String str) {
        return pascalToSeparator(str, "-");
    }

    private static String pascalToSeparator(String str, String separator) {

        // Empty String
        String result = "";

        // Append first character(in lower case)
        // to result string
        char c = str.charAt(0);
        result = result + Character.toLowerCase(c);

        // Traverse the string from
        // ist index to last index
        for (int i = 1; i < str.length(); i++) {

            char ch = str.charAt(i);

            // Check if the character is upper case
            // then append '_' and such character
            // (in lower case) to result string
            if (Character.isUpperCase(ch)) {
                result = result + separator;
                result = result
                        + Character.toLowerCase(ch);
            }

            // If the character is lower case then
            // add such character into result string
            else {
                result = result + ch;
            }
        }

        // return the result
        return result;
    }

    public static String toTechnicalName(String displayName) {
        var name = org.apache.commons.lang3.StringUtils.stripAccents(displayName);
        // replace all special characters with an underscore
        name = name.replaceAll("[^a-zA-Z0-9]", "_");
        name = name.toLowerCase();
        return name;
    }

    public static boolean isStringLong(String l) {
        try {
            Long.parseLong(l);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static Boolean toBoolean(String s) {
        if (s == null)
            return null;
        if (s.equalsIgnoreCase("true"))
            return true;
        if (s.equalsIgnoreCase("false"))
            return false;
        return null;
    }

}
