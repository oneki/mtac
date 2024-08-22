package net.oneki.mtac.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.oneki.mtac.model.entity.HasId;
import net.oneki.mtac.model.entity.HasLabel;

public class SetUtils {
  @SuppressWarnings("unchecked")
  public static <T> Set<T> of(T... elements) {
    Set<T> set = new HashSet<>();
    for (T element : elements) {
      set.add(element);
    }
    return set;
  }

  /**
   * extract the single element of a set
   */
  public static <T> T asElement(Set<T> set) {
    if (set == null) {
      return null;
    }
    for (T element : set) {
      return element;
    }
    return null;
  }

  public static <T extends HasId> void replaceById(Set<T> set, T newElement) {
    set.removeIf(entry -> entry.getId().equals(newElement.getId()));
    set.add(newElement);
  }

  public static <T extends HasLabel> void replaceByLabel(Set<T> set, T newElement) {
    set.removeIf(entry -> entry.getLabel().equals(newElement.getLabel()));
    set.add(newElement);
  }

  public static <T extends HasId> boolean containsById(Collection<T> set, T element) {
    return set.stream().filter(e -> e.getId().equals(element.getId())).findFirst().isPresent();
  }

  public static <T extends HasId> void removeById(Set<T> set, Collection<T> remove) {
    if (set != null && remove != null) {
      set.removeIf(entry -> containsById(remove, entry));
    }
  }

  public static <T extends HasId> void removeById(Set<T> set, T remove) {
    if (set != null && remove != null) {
      set.removeIf(entry -> entry.getId().equals(remove.getId()));
    }
  }
}
