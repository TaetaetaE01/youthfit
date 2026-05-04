-- 관심 분야(카테고리·시/도) 저장 테이블
CREATE TABLE IF NOT EXISTS notification_interest_category (
    notification_setting_id bigint NOT NULL
        REFERENCES notification_setting(id) ON DELETE CASCADE,
    category varchar(20) NOT NULL,
    PRIMARY KEY (notification_setting_id, category)
);

CREATE TABLE IF NOT EXISTS notification_interest_region (
    notification_setting_id bigint NOT NULL
        REFERENCES notification_setting(id) ON DELETE CASCADE,
    sido_code varchar(10) NOT NULL,
    PRIMARY KEY (notification_setting_id, sido_code)
);
