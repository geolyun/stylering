package com.stylering.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_items")
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatalogItemType type;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String brand;

    @Column(nullable = false, length = 32)
    private String priceRange;

    @Lob
    @Column(nullable = false)
    private String tagsJson;

    @Column(length = 32)
    private String gender;

    @Column(length = 32)
    private String season;

    @Column(length = 512)
    private String productUrl;

    @Column(length = 512)
    private String imageUrl;

    protected CatalogItem() {
    }

    public CatalogItem(
            CatalogItemType type,
            String name,
            String brand,
            String priceRange,
            String tagsJson,
            String gender,
            String season
    ) {
        this.type = type;
        this.name = name;
        this.brand = brand;
        this.priceRange = priceRange;
        this.tagsJson = tagsJson;
        this.gender = gender;
        this.season = season;
    }

    public CatalogItem(
            CatalogItemType type,
            String name,
            String brand,
            String priceRange,
            String tagsJson,
            String gender,
            String season,
            String productUrl
    ) {
        this(type, name, brand, priceRange, tagsJson, gender, season);
        this.productUrl = productUrl;
    }

    public Long getId() {
        return id;
    }

    public CatalogItemType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public String getGender() {
        return gender;
    }

    public String getSeason() {
        return season;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
