package com.sparta.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created_at;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String created_by;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated_at;

    @LastModifiedBy
    @Column(nullable = false)
    private String updated_by;

    @Column
    private LocalDateTime deleted_at;

    @Column
    private String deleted_by;

    protected void softDeleted(String deleted_by){
        this.deleted_at = LocalDateTime.now();
        this.deleted_by = deleted_by;
    }

    protected void restore(){
        this.deleted_at = null;
        this.deleted_by = null;
    }

    public boolean isDeleted(){ // 외부에서 상태 확인 용도로 쓰이기 때문에 public이 맞음
        return this.deleted_at != null;
    }

}
