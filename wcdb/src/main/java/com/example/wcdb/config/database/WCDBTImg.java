package com.example.wcdb.config.database;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Unique;

@Entity(nameInDb = "wcdb_t_img")
public class WCDBTImg {
    @Id(autoincrement = true)
    @Unique
    public Long id;  //主键自增长，不可重复,作为不同记录对象的标识，传入参数对象时不要传入

    @Generated(hash = 1267452662)
    public WCDBTImg(Long id) {
        this.id = id;
    }

    @Generated(hash = 1774823615)
    public WCDBTImg() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
