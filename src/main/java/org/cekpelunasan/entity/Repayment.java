package org.cekpelunasan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "repayment")
@Table(name = "repayment", indexes = {@Index(name = "name_idx", columnList = "name")})
public class Repayment {

    @Id
    @Column(name = "customer_id")
    private String customerId;

    private String product;

    private String name;

    private String address;

    private Long amount;

    private Long interest;

    private Long sistem;

    @Column(name = "penalty_loan")
    private Long penaltyLoan;

    @Column(name = "penalty_repayment")
    private Long penaltyRepayment;

    @Column(name = "total_pay")
    private Long totalPay;

    private String branch;

    @Column(name = "start_date")
    private String startDate;

    private Long plafond;

    private String lpdb;

    @Temporal(TemporalType.DATE)
    private Date createdAt;


}
