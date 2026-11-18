package com.growit.dot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dot_goals")
public class DotGoal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 50)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 10)
	private DotSourceType sourceType;

	/** 기본 그림일 때만 값이 있다. */
	@Column(name = "preset_key", length = 30)
	private String presetKey;

	/** 칠해야 하는 "기록한 날" 수. 기간이 아니라 날 수라서 기한은 없다. */
	@Column(name = "goal_days", nullable = false)
	private int goalDays;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	/** 칸 목록(x, y, 색 번호). 채워지는 순서대로 놓여 있고 길이는 goalDays와 같다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "grid_json", nullable = false, columnDefinition = "JSON")
	private String gridJson;

	/** 색 문자열 배열. 칸의 색 번호가 이 배열의 위치를 가리킨다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "palette_json", nullable = false, columnDefinition = "JSON")
	private String paletteJson;

	/** 이 날짜 이후(포함)의 기록만 칸으로 센다. 서버가 Asia/Seoul 기준으로 정한다. */
	@Column(name = "started_on", nullable = false)
	private LocalDate startedOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private DotGoalState state = DotGoalState.ACTIVE;

	@Column(name = "completed_on")
	private LocalDate completedOn;

	/** 완성 연출을 한 번 본 뒤 true. 기기를 바꿔도 다시 보이지 않게 서버에 둔다. */
	@Column(name = "completion_seen", nullable = false, columnDefinition = "BOOLEAN")
	private boolean completionSeen;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	protected DotGoal() {
	}

	public DotGoal(Long userId, String title, DotSourceType sourceType, String presetKey, int goalDays, int width,
			int height, String gridJson, String paletteJson, LocalDate startedOn) {
		this.userId = userId;
		this.title = title;
		this.sourceType = sourceType;
		this.presetKey = presetKey;
		this.goalDays = goalDays;
		this.width = width;
		this.height = height;
		this.gridJson = gridJson;
		this.paletteJson = paletteJson;
		this.startedOn = startedOn;
	}

	public void complete(LocalDate date) {
		this.state = DotGoalState.COMPLETED;
		this.completedOn = date;
	}

	public void stop() {
		this.state = DotGoalState.STOPPED;
	}

	public void markCompletionSeen() {
		this.completionSeen = true;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getTitle() {
		return title;
	}

	public DotSourceType getSourceType() {
		return sourceType;
	}

	public String getPresetKey() {
		return presetKey;
	}

	public int getGoalDays() {
		return goalDays;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String getGridJson() {
		return gridJson;
	}

	public String getPaletteJson() {
		return paletteJson;
	}

	public LocalDate getStartedOn() {
		return startedOn;
	}

	public DotGoalState getState() {
		return state;
	}

	public LocalDate getCompletedOn() {
		return completedOn;
	}

	public boolean isCompletionSeen() {
		return completionSeen;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
