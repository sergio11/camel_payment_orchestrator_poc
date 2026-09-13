package com.poc.gateway.infrastructure.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "payment_metadata")
public class PaymentMetadataEntity extends PanacheEntityBase {

    @Id
    @Column(name = "payment_id")
    public UUID paymentId;

    @OneToOne
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    public PaymentEntity payment;

    @Column(name = "order_id", length = 100)
    public String orderId;

    @Column(name = "attempts")
    public Integer attempts;

    @Column(name = "is_new_payment_method")
    public Boolean isNewPaymentMethod;

    @Column(name = "payment_method_age_days")
    public Integer paymentMethodAgeDays;

    @Column(name = "customer_risk_tier", length = 20)
    public String customerRiskTier;

    @Column(name = "enriched_at", length = 30)
    public String enrichedAt;

    @Column(name = "velocity_score")
    public Integer velocityScore;

    @Column(name = "geo_risk_score")
    public Integer geoRiskScore;

    @Column(name = "additional_properties", length = 2048)
    public String additionalProperties;
}
