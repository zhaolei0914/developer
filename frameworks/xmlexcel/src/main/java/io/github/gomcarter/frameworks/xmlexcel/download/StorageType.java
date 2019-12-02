package io.github.gomcarter.frameworks.xmlexcel.download;

import io.github.gomcarter.frameworks.base.common.NacosClientUtils;

public enum StorageType {
    /**
     * 文件存放本地的一个文件目录下，nfs挂载此目录，即可在公网上下载
     */
    nfs {
        @Override
        public String getDownloadUrl(Downloader downloader, String fileName) {
            // FIXME
            return NacosClientUtils.getConfigAsString("nfs", "STORAGE_TYPE") + fileName;
        }
    },
    oss {
        @Override
        public String getDownloadUrl(Downloader downloader, String fileName) {
            // FIXME
            return null;
        }
    };

    public abstract String getDownloadUrl(Downloader downloader, String fileName);
}