package com.portcelana.natiart.service;

import java.math.RoundingMode;

import com.portcelana.natiart.event.OrderMilestoneEvent;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;

record OrderMailContent(String subject, String body) {
    static OrderMailContent from(OrderMilestoneEvent event, String publicUrl) {
        final CustomerOrder order = event.order();
        final String headline =
                switch (event.status()) {
                    case PENDING -> "Pedido recebido / Order received";
                    case PAID -> "Pagamento confirmado / Payment confirmed";
                    case PROCESSING -> "Sua peça está sendo preparada / Your piece is being prepared";
                    case SHIPPED -> "Sua peça está a caminho / Your piece is on its way";
                    case DELIVERED -> "Entrega registrada / Delivery recorded";
                    case CANCELLED -> "Pedido cancelado / Order cancelled";
                };
        final String explanation =
                switch (event.status()) {
                    case PENDING ->
                        "O pagamento ainda não foi confirmado. Continue o PIX no site. / Payment is not yet confirmed. Continue PIX on the website.";
                    case PAID -> "Recebemos a confirmação do pagamento. / We have received your payment confirmation.";
                    case PROCESSING ->
                        "Estamos preparando e embalando sua encomenda com cuidado. / We are carefully preparing and packing your order.";
                    case SHIPPED ->
                        "Entregamos sua encomenda ao transportador. / We have handed your order to the carrier.";
                    case DELIVERED ->
                        "Sua entrega foi registrada pela loja. Se não recebeu, entre em contato. / The shop has recorded delivery. Contact us if it has not arrived.";
                    case CANCELLED -> "Este pedido não será preparado. / This order will not be prepared.";
                };
        final StringBuilder text = new StringBuilder("NatiArt | Feito com cuidado / Made with care\n\n")
                .append(headline)
                .append("\n")
                .append(explanation)
                .append("\n\nPedido / Order #")
                .append(order.getId().substring(0, 8))
                .append("\n");
        order.getItems()
                .forEach(item -> text.append(item.getQuantity())
                        .append(" x ")
                        .append(item.getProductLabel())
                        .append("\n"));
        text.append("Total: BRL ")
                .append(order.getTotalAmount().setScale(2, RoundingMode.UNNECESSARY))
                .append("\n\n");
        if (event.status() == OrderStatus.SHIPPED && order.getTrackingCode() != null) {
            text.append("Rastreio / Tracking: ").append(order.getTrackingCode()).append("\n");
            if (order.getTrackingUrl() != null)
                text.append(order.getTrackingUrl()).append("\n");
            text.append("\n");
        }
        if (order.getGuestCustomerId() != null) {
            text.append(
                            "Acompanhe sem criar uma conta: solicite um link seguro no email usado na compra. / Track without creating an account: request a secure link at your checkout email.\n")
                    .append(publicUrl)
                    .append("/pt-BR/claim-orders\n\n")
                    .append("English:\n")
                    .append(publicUrl)
                    .append("/en/claim-orders\n");
        } else {
            text.append("Entre na sua conta para acompanhar / Sign in to follow your order:\n")
                    .append(publicUrl)
                    .append("/pt-BR/account?orderId=")
                    .append(order.getId())
                    .append("\n\n")
                    .append("English:\n")
                    .append(publicUrl)
                    .append("/en/account?orderId=")
                    .append(order.getId())
                    .append("\n");
        }
        text.append(
                "\nDúvidas sobre sua encomenda? Responda este e-mail. / Questions about your piece? Reply to this email.\n");
        text.append(
                "\nNunca pedimos sua senha ou pagamento por email. / We never ask for your password or payment by email.\n");
        return new OrderMailContent("NatiArt #" + order.getId().substring(0, 8) + " - " + headline, text.toString());
    }

    @Override
    public String toString() {
        return "OrderMailContent[redacted]";
    }
}
