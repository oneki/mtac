package net.oneki.mtac.model.framework;

import java.util.ArrayList;
import java.util.List;

public class RelationRefs {
    private List<Integer> ids = new ArrayList<>();
    private List<String> urns = new ArrayList<>();
    private List<RelationLabel> labels = new ArrayList<>();
    private List<HasSchema> embeddeds = new ArrayList<>();

    public void addEmbedded(HasSchema embedded) {
        embeddeds.add(embedded);
    }

    public void addEmbeddeds(List<HasSchema> embeddeds) {
        this.embeddeds.addAll(embeddeds);
    }

    public void addId(Integer id) {
        ids.add(id);
    }

    public void addIds(List<Integer> ids) {
        this.ids.addAll(ids);
    }

    public void addUrns(String urn) {
        urns.add(urn);
    }

    public void addUrns(List<String> urns) {
        this.urns.addAll(urns);
    }

    public void addLabel(RelationLabel relationLabel) {
        labels.add(relationLabel);
    }

    public void addLabels(List<RelationLabel> labels) {
        this.labels.addAll(labels);
    }

    public List<HasSchema> getEmbeddeds() {
        return embeddeds;
    }

    public List<Integer> getIds() {
        return ids;
    }

    public List<String> getUrns() {
        return urns;
    }

    public List<RelationLabel> getLabels() {
        return labels;
    }

    public boolean containsId(Integer id) {
        return ids.contains(id);
    }

    public boolean containsUrn(String urn) {
        return urns.contains(urn);
    }

    public boolean containsLabel(String label, Class<?> schema) {
        for (var relationLabel: labels) {
            if (relationLabel.getLabel().equals(label) && relationLabel.getSchema().isAssignableFrom(schema)) {
                return true;
            }
        }
        return false;
    }

    public void add(RelationRefs refs) {
        addIds(refs.getIds());
        addUrns(refs.getUrns());
        addLabels(refs.getLabels());
    }

}
