package com.github.tvbox.osc.bean;

import java.io.Serializable;
import java.util.List;

/**
 * 多仓/多线路数据模型
 * 移植自影视仓 com.github.tvbox.osc.bean.MoreSourceBean
 * 字段语义与原版一致：
 *  - 作为"仓库"时：sourceName/sourceUrl 为仓库名与订阅地址，localLineUrls 为仓库内嵌线路
 *  - 作为"线路"时：sourceName/sourceUrl 为线路名与接口地址
 */
public class MoreSourceBean implements Serializable {
    private String sourceName = "";
    private String sourceUrl = "";
    private boolean isSelected = false;
    private boolean showDelete = false;
    private boolean isLocalPackage = false;
    /** 接口配置文件中的直播线路序号（-1 表示用户自行添加的直播源） */
    private int liveGroupIndex = -1;
    private List<MoreSourceBean> localLineUrls;

    public List<MoreSourceBean> getLocalLineUrls() {
        return this.localLineUrls;
    }

    public void setLocalLineUrls(List<MoreSourceBean> localLineUrls) {
        this.localLineUrls = localLineUrls;
    }

    public boolean isSelected() {
        return this.isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    public boolean isShowDelete() {
        return this.showDelete;
    }

    public void setShowDelete(boolean showDelete) {
        this.showDelete = showDelete;
    }

    public int getLiveGroupIndex() {
        return this.liveGroupIndex;
    }

    public void setLiveGroupIndex(int liveGroupIndex) {
        this.liveGroupIndex = liveGroupIndex;
    }

    public boolean isLocalPackage() {
        return this.isLocalPackage;
    }

    public void setLocalPackage(boolean localPackage) {
        this.isLocalPackage = localPackage;
    }

    public String getSourceName() {
        return this.sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName == null ? "" : sourceName;
    }

    public String getSourceUrl() {
        return this.sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl == null ? "" : sourceUrl;
    }

    /** 唯一键：与影视仓 getUniKey 一致，用于列表去重 */
    public String getUniKey() {
        return String.valueOf((this.sourceUrl + this.sourceName).hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MoreSourceBean)) return false;
        MoreSourceBean that = (MoreSourceBean) o;
        return getUniKey().equals(that.getUniKey());
    }

    @Override
    public int hashCode() {
        return getUniKey().hashCode();
    }
}
