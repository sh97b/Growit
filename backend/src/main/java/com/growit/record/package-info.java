/**
 * 활동 기록({@link com.growit.record.entity.ActivityRecord}) 등록·조회·수정·삭제를 다룬다.
 * <p>모든 기록은 활동 분야({@code com.growit.hobby}) 하나에 속하고, 기록을 고치면
 * {@code ai_analysis}가 초기화된다. "오늘"과 미래 날짜 판단은 서버 시간대(Asia/Seoul) 기준이다.
 */
package com.growit.record;
