package com.example.springmvcapp;

import java.io.IOException;

import org.bitcoinj.core.Context;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Фильтр, устанавливающий bitcoinj {@link Context} для каждого потока Tomcat.
 *
 * <p>bitcoinj требует, чтобы каждый поток, обращающийся к кошельку, имел
 * установленный Context. Без этого возникают ошибки "thread fixup" и
 * потенциально повреждаются файлы кошельков при параллельной записи.</p>
 *
 * <p>Фильтр вызывает {@link Context#propagate(Context)} перед обработкой
 * каждого HTTP-запроса, гарантируя корректную работу bitcoinj во всех
 * потоках пула Tomcat.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BitcoinContextFilter extends OncePerRequestFilter {

    private volatile Context bitcoinContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Context ctx = this.bitcoinContext;
        if (ctx == null) {
            ctx = Context.getOrCreate();
            this.bitcoinContext = ctx;
        }
        Context.propagate(ctx);
        filterChain.doFilter(request, response);
    }
}
