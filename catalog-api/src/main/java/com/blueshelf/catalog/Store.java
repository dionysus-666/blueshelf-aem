package com.blueshelf.catalog;

public record Store(String id, String name, String address, String city, String state, String zip, double lat, double lng, String hours) {}
