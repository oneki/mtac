package net.oneki.mtac.core.security;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Ace {
    private List<String> schemas;
    private List<String> fields;
    private List<String> actions;
}
