package net.oneki.mtac.framework.util.sql;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import net.oneki.mtac.framework.introspect.ResourceDesc;
import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.framework.util.sql.SqlReader;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.util.exception.BusinessException;

public class SqlUtils {
    protected static Map<String, String> sqlMap = new HashMap<>();
    public static String ROW_NUMBER_COLUMN = "$rowNumber$";

    public static String getSQL(String path) {
        String result = sqlMap.get(path);
        if (result != null) {
            return result;
        }
        String sql = SqlReader.getSQL(path);
        sqlMap.put(path, SqlReader.getSQL(path));
        return sql;
    }

    public static Map<String, Object> getPropertySqlParameters(Object entity) {
        Map<String, Object> parameters = new HashMap<>();
        SqlParameterSource args = new BeanPropertySqlParameterSource(entity);
        for (String parameter : args.getParameterNames()) {
            parameters.put(parameter, args.getValue(parameter));
        }
        return parameters;
    }

    public static Map<String, String> getLeftOperand(ResourceDesc type, String field, Map<String, String> tableAliases,
            String jsonbOperator) {
        Map<String, String> ret = new HashMap<>();
        String operand = "";
        String table = "";
        String leftJoinTable = "";
        // If the resource is a link, use the content of the resource pointed to by the
        // link.
        String column = "(CASE WHEN r.link_id is null THEN " + tableAliases.get("resource") + ".content" + " ELSE "
                + tableAliases.get("link") + ".content" + " END)";
        String jsonbOperand = "";

        switch (field) {
            case "id":
                operand += tableAliases.get("resource") + ".id";
                break;
            case "urn":
                operand += tableAliases.get("resource") + ".urn";
                break;
            case "label":
                operand += tableAliases.get("resource") + ".label";
                break;
            case "pub":
            case "public":
                operand += tableAliases.get("resource") + ".pub";
                break;
            case "tenant":
            case "tenant.label":
                // we don't have the tenant_label in the resource table, only the tenant_id
                // so we need to do a join to filter on the tenant's label
                String tenantTableName = generateUniqueTableName("tenant");
                leftJoinTable += " LEFT JOIN resource " + tenantTableName + " ON " + tableAliases.get("resource")
                        + ".tenant_id = " + tenantTableName + ".id";
                operand += tenantTableName + ".label";
                break;
            case "tenant.id":
                operand += tableAliases.get("resource") + ".tenant_id";
                break;
            case "schema":
            case "schema.label":
                // same principle as for tenant
                String schemaTableName = generateUniqueTableName("schema");
                leftJoinTable += " LEFT JOIN resource " + schemaTableName + " ON " + tableAliases.get("resource")
                        + ".schema_id = " + schemaTableName + ".id";
                operand += schemaTableName + ".label";
                break;
            case "schema.id":
                operand += tableAliases.get("resource") + ".schema_id";
                break;
            case "link_type":
                operand += tableAliases.get("resource") + ".link_type";
                break;
            case "resource_type":
                operand += tableAliases.get("resource") + ".resource_type";
                break;
            case "created_by":
                operand += tableAliases.get("resource") + ".created_by";
                break;
            case "updated_by":
                operand += tableAliases.get("resource") + ".updated_by";
                break;
            case "created_at":
                operand += tableAliases.get("resource") + ".created_at";
                break;
            case "updated_at":
                operand += tableAliases.get("resource") + ".updated_at";
                break;
            default:
                // We search on a field that is present in the content of the resource
                if (field.endsWith(".\"" + SqlUtils.ROW_NUMBER_COLUMN + "\"")) {
                    operand = field;
                    String[] tokens = field.split("\\.");
                    table += ", " + tokens[0];
                } else {
                    if (type == null) {
                        throw new BusinessException(BusinessException.BUSINESS_INVALID_FIELD,
                                "Cannot filter on field " + field + " when resourceContentType is not specified");
                    }

                    // Example: the field could be "firstName" or "memberOf.label"
                    String[] parts = field.split("\\.");

                    ResourceField fieldDef = type.getField(parts[0]);
                    if (fieldDef != null && fieldDef.isMultiple()) {
                        // If the field we search on is an array or a list, we must create a temporary
                        // table via the postgres function json_array_elements
                        String tableName = generateUniqueTableName("jae");
                        leftJoinTable += " LEFT JOIN json_array_elements((" + column + "->'" + parts[0] + "')::json) "
                                + tableName + " ON TRUE";
                        if (parts.length > 1) {
                            // Example: the field is "memberOf.label" (memberOf is an array)
                            // Example of WHERE clause corresponding to memberOf.label : WHERE jae_table #>>
                            // '{label}' = 'value'
                            jsonbOperand = tableName + " " + jsonbOperator + "'{"
                                    + String.join(",", Arrays.copyOfRange(parts, 1, parts.length)) + "}'";
                            if (parts[parts.length - 1].equals("id")) {
                                // special case for the field "id" (exemple: memberOf.id)
                                // We know id is an integer
                                jsonbOperand = "(" + jsonbOperand + ") is not NULL AND (" + jsonbOperand + ")::integer";
                            }
                            operand += jsonbOperand;
                        } else {
                            // Example: the field is "memberOf" (memberOf is an array)
                            // Example of WHERE clause corresponding to memberOf : WHERE jae_table #>> '{}'
                            // = 'value'
                            operand += tableName + " " + jsonbOperator + "'{}'";
                        }

                    } else {
                        // Example: the field is "firstName"
                        // Example of WHERE clause corresponding to firstName : WHERE r.content #>>
                        // '{firstName}' = 'value'
                        jsonbOperand = column + " " + jsonbOperator + "'{" + String.join(",", parts) + "}'";
                        if (parts[parts.length - 1].equals("id")) {
                            jsonbOperand = "(" + jsonbOperand + ") is not NULL AND (" + jsonbOperand + ")::integer";
                        }
                        operand += jsonbOperand;
                    }
                }
        }
        ret.put("leftJoinTable", leftJoinTable);
        ret.put("table", table);
        ret.put("operand", operand);
        return ret;
    }

    public static String generateUniqueTableName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static String formatSqlQuery(String sql, Map<String, Object> args) {
        for (var entry : args.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            String sqlValue = "";

            if (value instanceof Object[]) {
                value = Arrays.asList(value);
            }

            if (value instanceof Collection<?>) {
                sqlValue = ((Collection<?>) value).stream().map(v -> toSqlString(v)).collect(Collectors.joining(","));
            } else {
                sqlValue = toSqlString(value);
            }
            sql = sql.replaceAll(":" + key, sqlValue);
        }
        return sql;
    }

    private static String toSqlString(Object arg) {
        if (arg == null) {
            return "NULL";
        }
        if (arg instanceof String) {
            return "'" + arg.toString() + "'";
        }
        return arg.toString();
    }

}
