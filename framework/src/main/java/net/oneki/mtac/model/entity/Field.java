package net.oneki.mtac.model.entity;


import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.oneki.mtac.util.introspect.ResourceField;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Field {
    private Integer id;
    private String label;
    private String type;
    private String owner;

    @EqualsAndHashCode.Exclude
    private Integer peerId;
    private Boolean multiple;
    private Boolean required;
    private Object validators;
    private Object defaultValue;
    private Boolean priv;
    private String description;
    private Boolean editable;
    private Object example;

    @EqualsAndHashCode.Exclude
    private List<String> schemas;

    @EqualsAndHashCode.Exclude
    private transient ResourceField scannedField;
}
