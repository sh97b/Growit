package com.growit.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "custom_feelings")
public class CustomFeeling {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 8)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private FeelingDirection direction;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected CustomFeeling() {
	}

	public CustomFeeling(Long userId, String label, FeelingDirection direction) {
		this.userId = userId;
		this.label = label;
		this.direction = direction;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getLabel() {
		return label;
	}

	public FeelingDirection getDirection() {
		return direction;
	}
}
