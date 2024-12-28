package net.oneki.mtac.core.util.query;

import java.util.LinkedHashMap;
import java.util.Map;

import net.oneki.mtac.core.util.query.Relation;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.oneki.mtac.core.util.introspect.ResourceDesc;
import net.oneki.mtac.core.util.sql.SqlUtils;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SortCriteria {
  private ResourceDesc type;
  private String sortBy;
  private String order;
  private Relation relation;

	@Builder(builderMethodName = "sortCriteriaBuilder")
	public SortCriteria(ResourceDesc type, String field, String order, Relation relation) {
    this.type = type;
    this.sortBy = field;
    this.order = order;
    this.relation = relation;
  }

  public Map<String, String> toSql(Map<String,String> tableAliases) {
    String sortBy = this.sortBy;
    String order = this.order;
    if (relation != null) {
      sortBy = relation.getTable() + ".\"" + SqlUtils.ROW_NUMBER_COLUMN + "\"";
    }
    if (order == null) {
      order = "ASC";
    }

    Map<String,String> ret = SqlUtils.getLeftOperand(type, sortBy, tableAliases, "#>>");
    ret.put("order_by", ret.get("operand") + ((order.toLowerCase().equals("desc")) ? " DESC" : " ASC"));

    return ret;
  }
}
