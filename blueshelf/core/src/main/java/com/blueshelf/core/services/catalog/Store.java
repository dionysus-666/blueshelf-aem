package com.blueshelf.core.services.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties
public class Store {
      private String id;
      private String name;
      private String address;
      private String city;
      private String state;
      private String zip;
      private String hours;

      public String getId() { return id; }
      public String getName() { return name; }
      public String getAddress() { return address; }
      public String getCity() { return city; }
      public String getState() { return state; }
      public String getZip() { return zip; }
      public String getHours() { return hours; }

      public void setId(String id) { this.id = id; }
      public void setName(String name) { this.name = name; }
      public void setAddress(String address) { this.address = address; }
      public void setCity(String city) { this.city = city; }
      public void setState(String state) { this.state = state; }
      public void setZip(String zip) { this.zip = zip; }
      public void setHours(String hours) { this.hours = hours; }
  }