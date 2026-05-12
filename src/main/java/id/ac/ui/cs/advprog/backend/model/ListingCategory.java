package id.ac.ui.cs.advprog.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum ListingCategory {
    ELECTRONICS(null, "Elektronik"),
    ELECTRONICS_PHONE(ELECTRONICS, "Handphone"),
    ELECTRONICS_SMARTPHONE(ELECTRONICS_PHONE, "Smartphone"),
    ELECTRONICS_LAPTOP(ELECTRONICS, "Laptop"),
    FASHION(null, "Fashion"),
    FASHION_MENSWEAR(FASHION, "Pria"),
    FASHION_WOMENSWEAR(FASHION, "Wanita"),
    BOOKS(null, "Buku"),
    BOOKS_FICTION(BOOKS, "Fiksi"),
    HOME_LIVING(null, "Rumah Tangga"),
    HOME_LIVING_FURNITURE(HOME_LIVING, "Furniture"),
    BEAUTY(null, "Kecantikan"),
    SPORTS(null, "Olahraga"),
    HOBBIES(null, "Hobi"),
    OTHER(null, "Lainnya");

    private final ListingCategory parent;
    private final String label;

    ListingCategory(ListingCategory parent, String label) {
        this.parent = parent;
        this.label = label;
    }

    public ListingCategory parent() {
        return parent;
    }

    public String label() {
        return label;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean isSameOrDescendantOf(ListingCategory category) {
        if (category == null) {
            return true;
        }

        ListingCategory current = this;
        while (current != null) {
            if (current == category) {
                return true;
            }
            current = current.parent;
        }
        return false;
    }

    public String pathLabel() {
        return String.join(" > ", pathSegments());
    }

    public List<String> pathSegments() {
        List<String> segments = new ArrayList<>();
        ListingCategory current = this;
        while (current != null) {
            segments.add(current.label);
            current = current.parent;
        }
        Collections.reverse(segments);
        return segments;
    }

    public List<ListingCategory> children() {
        return List.of(values()).stream()
            .filter(category -> category.parent == this)
            .toList();
    }
}
