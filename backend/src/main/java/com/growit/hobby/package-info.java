/**
 * 활동 분야({@link com.growit.hobby.entity.Hobby}) 등록·조회·수정·삭제·보관을 다룬다.
 * <p>보관(archive)은 삭제와 다르다: 보관된 분야는 조회·기존 기록 열람은 되지만 새 기록을 쓸 수 없고,
 * 삭제는 기록이 하나도 없을 때만 가능하다. {@code record} 도메인이 분야 번호로 기록을 연결한다.
 */
package com.growit.hobby;
