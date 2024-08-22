package net.oneki.mtac.model.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.oneki.mtac.model.framework.HasSchema;
import net.oneki.mtac.model.framework.RelationLabel;
import net.oneki.mtac.model.framework.RelationRefs;

public class RelationRefs {
    private List<Integer> ids = new ArrayList<>();
    private List<UUID> publicIds = new ArrayList<>();
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

    public void addPublicId(UUID id) {
        publicIds.add(id);
    }

    public void addPublicIds(List<UUID> publicIds) {
        this.publicIds.addAll(publicIds);
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

    public List<UUID> getPublicIds() {
        return publicIds;
    }

    public List<RelationLabel> getLabels() {
        return labels;
    }

    public boolean containsId(Integer id) {
        return ids.contains(id);
    }

    public boolean containsPublicId(UUID id) {
        return publicIds.contains(id);
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
        addPublicIds(refs.getPublicIds());
        addLabels(refs.getLabels());
    }

}
