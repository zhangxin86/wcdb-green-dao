package com.example.wcdb.entity;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Unique;
import org.greenrobot.greendao.annotation.Generated;

@Entity(nameInDb = "t_user")
public class User {
    @Id
    @Unique
    public Long uid;  //主键自增长，不可重复,作为不同记录对象的标识，传入参数对象时不要传入

    @Property(nameInDb = "userId")
    public String userId;

    @Property(nameInDb = "userName")
    public String userName;

    @Property(nameInDb = "age")
    public int age;

    @Property(nameInDb = "sex")
    public String sex;

    @Generated(hash = 1897020330)
    public User(Long uid, String userId, String userName, int age, String sex) {
        this.uid = uid;
        this.userId = userId;
        this.userName = userName;
        this.age = age;
        this.sex = sex;
    }

    @Generated(hash = 586692638)
    public User() {
    }

    public Long getUid() {
        return this.uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getAge() {
        return this.age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getSex() {
        return this.sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
    
}
