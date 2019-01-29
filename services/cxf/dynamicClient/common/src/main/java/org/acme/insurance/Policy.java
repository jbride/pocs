package org.acme.insurance;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "policy", propOrder = {
    "driver",
    "policyType",
    "price",
    "priceDiscount",
    "vehicleYear"
})
public class Policy implements Serializable {

    private static final long serialVersionUID = 1L;
    private String policyType;
    private int vehicleYear;
    private int price;
    private int priceDiscount;
    private Driver driver;

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("\nPolicy \n\tpolicyType = ");
        sBuilder.append(policyType);
        sBuilder.append("\n\tvehicleYear = ");
        sBuilder.append(vehicleYear);
        sBuilder.append("\n\tprice = ");
        sBuilder.append(price);
        sBuilder.append("\n\tpriceDiscount = ");
        sBuilder.append(priceDiscount);
        if(driver != null)
            sBuilder.append(driver.toString());
        else
            sBuilder.append("\n\tDriver = null");
        return sBuilder.toString();
    }

    public Policy() {
        driver = new Driver();
    }
    public String getPolicyType() {
        return policyType;
    }
    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }
    public int getVehicleYear() {
        return vehicleYear;
    }
    public void setVehicleYear(int vehicleYear) {
        this.vehicleYear = vehicleYear;
    }
    public int getPrice() {
        return price;
    }
    public void setPrice(int price) {
        this.price = price;
    }
    public int getPriceDiscount() {
        return priceDiscount;
    }
    public void setPriceDiscount(int x) {
        priceDiscount = x;
    }
    public Driver getDriver() {
        return driver;
    }
    public void setDriver(Driver driver) {
        this.driver = driver;
    }
}
