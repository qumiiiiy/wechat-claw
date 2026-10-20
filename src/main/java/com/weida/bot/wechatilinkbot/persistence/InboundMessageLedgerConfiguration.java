package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.persistence.mybatis.InboundMessageLedgerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects durable ledger storage when the configured SQLite mapper is available. */
@Configuration(proxyBeanMethods = false)
public class InboundMessageLedgerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(InboundMessageLedgerConfiguration.class);

    @Bean
    InboundMessageLedger inboundMessageLedger(
            InboundMessageLedgerProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        try {
            InboundMessageLedgerMapper mapper =
                    beanFactory.getBean(InboundMessageLedgerMapper.class);
            LOG.info("inbound_message_ledger_provider_selected provider=sqlite");
            return new SqliteInboundMessageLedger(mapper, properties);
        } catch (Exception exception) {
            LOG.warn(
                    "inbound_message_ledger_sqlite_unavailable reason={} "
                            + "falling_back_to_in_memory",
                    exception.getMessage());
        }
        LOG.info("inbound_message_ledger_provider_selected provider=in_memory");
        return new InMemoryInboundMessageLedger(properties);
    }
}
