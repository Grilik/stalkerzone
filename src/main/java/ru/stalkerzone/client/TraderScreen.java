package ru.stalkerzone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.stalkerzone.net.Net;
import ru.stalkerzone.net.Net.TraderView;
import ru.stalkerzone.npc.TraderWindow;
import ru.stalkerzone.sim.PdaSnapshot;

import java.util.List;

/**
 * П-07: окно торговца по наброску docs/ui/p07-trader.png. Слева — товары торговца, справа — ваш рюкзак с ценой,
 * за которую торговец купит каждую вещь (серым — не берёт); внизу — сделка (1 / 10 / всё) с итогом до подтверждения,
 * касса торговца, ваши деньги и вес. Вкладки: «Торговля», «Заказы и контракты», «Ремонт».
 * Окно только просит: сделку проверяет и проводит сервер ({@code npc.TraderWindow}), итог считает тоже он.
 */
public class TraderScreen extends Screen {
    private static final String[] TABS = {"Торговля", "Заказы и контракты", "Ремонт"};
    private static final int BG = 0xF00C120C, PANEL = 0xF0162016, FRAME = 0xFF4E6B44, TEXT = 0xFFD8E0C8, DIM = 0xFF7C8A72,
            ACCENT = 0xFFE8C060, SEL = 0x553E6A2A, WARN = 0xFFE08A4A;
    private static final int ROW = 18;

    private TraderView view;
    private int tab;
    private int side = -1, index = -1, qty = 1;
    private int scrollL, scrollR, scrollC;
    private int left, top, w, h;

    public TraderScreen(TraderView view) {
        super(Component.literal("Торговец"));
        this.view = view;
    }

    /** Клиент получил состояние окна: обновить открытое, открыть новое или закрыть (торговец далеко). */
    public static void receive(TraderView v) {
        Minecraft mc = Minecraft.getInstance();
        if (!v.open && v.title.isEmpty()) {
            if (mc.screen instanceof TraderScreen) mc.setScreen(null);
            if (mc.player != null && !v.message.isEmpty()) mc.player.displayClientMessage(Component.literal(v.message), false);
            return;
        }
        if (mc.screen instanceof TraderScreen s && s.view.entity == v.entity) s.update(v);
        else if (v.open) mc.setScreen(new TraderScreen(v));
    }

    void update(TraderView v) {
        this.view = v;
        if (v.quote != null) { side = v.quote.side(); index = v.quote.index(); }
        rebuild();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        w = Math.min(width - 12, 560);
        h = Math.min(height - 12, 320);
        left = (width - w) / 2;
        top = (height - h) / 2;
        rebuild();
    }

    private void send(int action, int s, int i, int q) {
        Net.CHANNEL.sendToServer(new Net.TraderAction(action, view.entity, s, i, q));
    }

    private void quote() { if (side >= 0 && index >= 0) send(TraderWindow.QUOTE, side, index, qty); }

    // ------------------------------------------------------------------ раскладка

    private int listTop() { return top + 44; }
    private int listBottom() { return tab == 0 ? top + h - 74 : top + h - 26; }
    private int rowsVisible() { return Math.max(1, (listBottom() - listTop() - 14) / ROW); }
    private int colW() { return (w - 18) / 2; }

    private void rebuild() {
        clearWidgets();
        int x = left + 6;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            int bw = font.width(TABS[i]) + 14;
            addRenderableWidget(Button.builder(Component.literal((tab == i ? "▸ " : "") + TABS[i]), b -> { tab = t; rebuild(); })
                    .bounds(x, top + 22, bw + (tab == i ? 8 : 0), 16).build());
            x += bw + 12;
        }
        addRenderableWidget(Button.builder(Component.literal("↻"), b -> send(TraderWindow.REFRESH, 0, 0, 0))
                .bounds(left + w - 24, top + 22, 18, 16).build());
        if (tab == 0) tradeButtons();
        if (tab == 1) contractButtons();
        if (tab == 2) repairButtons();
    }

    private void tradeButtons() {
        int y = top + h - 70;
        int bx = left + 80;
        int all = maxQty();
        int[] qs = {1, 10, Math.max(1, all)};
        String[] labels = {"1", "10", "всё (" + all + ")"};
        for (int i = 0; i < 3; i++) {
            final int q = qs[i];
            Button b = Button.builder(Component.literal((qty == q && side >= 0 ? "▸" : "") + labels[i]), x -> { qty = q; quote(); rebuild(); })
                    .bounds(bx + i * 58, y + 14, 54, 16).build();
            b.active = side >= 0;
            addRenderableWidget(b);
        }
        TraderView.Quote q = view.quote;
        boolean ready = q != null && q.side() == side && q.index() == index && q.qty() > 0;
        Button buy = Button.builder(Component.literal("КУПИТЬ"), b -> send(TraderWindow.BUY, 0, index, qty))
                .bounds(left + w - 150, y + 6, 68, 24).build();
        buy.active = ready && side == TraderWindow.SIDE_BUY;
        Button sell = Button.builder(Component.literal("ПРОДАТЬ"), b -> send(TraderWindow.SELL, 1, index, qty))
                .bounds(left + w - 78, y + 6, 68, 24).build();
        sell.active = ready && side == TraderWindow.SIDE_SELL;
        addRenderableWidget(buy);
        addRenderableWidget(sell);
    }

    private int maxQty() {
        TraderView.Row r = selectedRow();
        return r == null ? 1 : Math.max(1, r.count());
    }

    private TraderView.Row selectedRow() {
        if (side < 0) return null;
        for (TraderView.Row r : side == 0 ? view.offers : view.items) if (r.index() == index) return r;
        return null;
    }

    private void contractButtons() {
        int y = listTop() + 14;
        int n = rowsVisible() / 2;
        List<TraderView.ContractRow> cs = view.contracts;
        scrollC = Math.max(0, Math.min(scrollC, cs.size() - n));
        for (int i = scrollC; i < cs.size() && i < scrollC + n; i++) {
            TraderView.ContractRow c = cs.get(i);
            int bx = left + w - 92;
            int act = c.flags();
            if ((act & PdaSnapshot.CAN_TAKE) != 0) bx = button(bx, y, "Взять", TraderWindow.TAKE, c.id());
            if ((act & PdaSnapshot.CAN_DELIVER) != 0) bx = button(bx, y, "Сдать", TraderWindow.DELIVER, c.id());
            if ((act & PdaSnapshot.CAN_CLAIM) != 0) bx = button(bx, y, "Награда", TraderWindow.CLAIM, c.id());
            if ((act & PdaSnapshot.CAN_ABANDON) != 0) button(bx, y + 17, "Отказаться", TraderWindow.ABANDON, c.id());
            y += ROW * 2;
        }
    }

    private int button(int x, int y, String label, int action, int id) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> send(action, 0, id, 1)).bounds(x, y, 80, 16).build());
        return x - 84;
    }

    private void repairButtons() {
        int y = listTop() + 14;
        List<TraderView.Row> rs = view.repairs;
        for (int i = 0; i < rs.size() && i < rowsVisible(); i++) {
            TraderView.Row r = rs.get(i);
            Button b = Button.builder(Component.literal("Починить"), x -> send(TraderWindow.REPAIR, 1, r.index(), 1))
                    .bounds(left + w - 86, y + 1, 76, 16).build();
            b.active = r.ok() && r.price() <= view.playerMoney;
            addRenderableWidget(b);
            y += ROW;
        }
    }

    // ------------------------------------------------------------------ мышь

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab != 0 || my < listTop() + 14 || my >= listBottom()) return false;
        int row = (int) ((my - listTop() - 14) / ROW);
        int lx = left + 6, rx = left + 12 + colW();
        if (mx >= lx && mx < lx + colW()) return pick(0, view.offers, scrollL + row);
        if (mx >= rx && mx < rx + colW()) return pick(1, view.items, scrollR + row);
        return false;
    }

    private boolean pick(int s, List<TraderView.Row> rows, int i) {
        if (i < 0 || i >= rows.size()) return false;
        TraderView.Row r = rows.get(i);
        if (!r.ok() && s == 1) return true;   // «не берёт» — выбирать нечего
        side = s;
        index = r.index();
        qty = 1;
        quote();
        rebuild();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = delta > 0 ? -1 : 1;
        if (tab == 1) { scrollC = Math.max(0, scrollC + d); rebuild(); return true; }
        if (tab != 0) return false;
        if (mx < left + 6 + colW()) scrollL = clamp(scrollL + d, view.offers.size());
        else scrollR = clamp(scrollR + d, view.items.size());
        return true;
    }

    private int clamp(int v, int size) { return Math.max(0, Math.min(v, size - rowsVisible())); }

    // ------------------------------------------------------------------ рисование

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + w, top + h, BG);
        g.renderOutline(left, top, w, h, FRAME);
        g.drawString(font, view.title, left + 8, top + 7, ACCENT, false);
        String note = view.note;
        g.drawString(font, note, left + w - 8 - font.width(note), top + 7, DIM, false);
        if (tab == 0) renderTrade(g, mx, my);
        if (tab == 1) renderContracts(g);
        if (tab == 2) renderRepairs(g);
        String foot = "касса торговца: " + rub(view.traderCash) + "    ваши деньги: " + rub(view.playerMoney)
                + "    вес: " + String.format("%.1f / %.0f кг", view.weight, view.limit);
        g.drawString(font, foot, left + 8, top + h - 20, TEXT, false);
        if (!view.message.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(view.message), w - 16);
            g.drawString(font, lines.get(lines.size() - 1), left + 8, top + h - 10, ACCENT, false);
        }
        super.render(g, mx, my, pt);
    }

    private void renderTrade(GuiGraphics g, int mx, int my) {
        int lx = left + 6, rx = left + 12 + colW(), y0 = listTop(), y1 = listBottom();
        panel(g, lx, y0, colW(), y1 - y0, "ТОВАРЫ ТОРГОВЦА", "есть", "цена");
        panel(g, rx, y0, colW(), y1 - y0, "ВАШ РЮКЗАК — торговец купит за", "шт.", "даст");
        scrollL = clamp(scrollL, view.offers.size());
        scrollR = clamp(scrollR, view.items.size());
        rows(g, lx, y0 + 14, view.offers, scrollL, 0, false);
        rows(g, rx, y0 + 14, view.items, scrollR, 1, true);

        // сделка
        int y = top + h - 70;
        g.fill(left + 6, y, left + w - 6, y + 44, PANEL);
        g.renderOutline(left + 6, y, w - 12, 44, ACCENT);
        TraderView.Row r = selectedRow();
        if (r == null) {
            g.drawString(font, "Выберите товар слева (купить) или вещь справа (продать).", left + 12, y + 4, DIM, false);
            return;
        }
        g.drawString(font, "СДЕЛКА: " + (side == 0 ? "купить " : "продать ") + cut(label(r), w - 200), left + 12, y + 4, TEXT, false);
        g.drawString(font, "кол-во:", left + 12, y + 18, DIM, false);
        TraderView.Quote q = view.quote;
        if (q == null || q.side() != side || q.index() != index) {
            g.drawString(font, "считаем…", left + 12, y + 33, DIM, false);
            return;
        }
        String total;
        if (q.qty() == 0) total = "не выйдет: " + q.reason();
        else {
            long after = side == 0 ? view.playerMoney - q.money() : view.playerMoney + q.money();
            total = "итого: " + q.qty() + " шт. = " + rub(q.money()) + "   у вас: " + rub(view.playerMoney) + " → " + rub(after)
                    + (q.qty() < q.asked() ? "   (" + q.reason() + ")" : "");
        }
        g.drawString(font, cut(total, w - 180), left + 12, y + 33, q.qty() == 0 ? WARN : ACCENT, false);
    }

    private void panel(GuiGraphics g, int x, int y, int pw, int ph, String title, String c1, String c2) {
        g.fill(x, y, x + pw, y + ph, PANEL);
        g.renderOutline(x, y, pw, ph, FRAME);
        g.drawString(font, title, x + 4, y + 3, ACCENT, false);
        g.drawString(font, c1, x + pw - 80 - font.width(c1), y + 3, DIM, false);
        g.drawString(font, c2, x + pw - 4 - font.width(c2), y + 3, DIM, false);
    }

    private void rows(GuiGraphics g, int x, int y, List<TraderView.Row> rows, int scroll, int s, boolean sell) {
        int pw = colW();
        for (int i = scroll; i < rows.size() && i < scroll + rowsVisible(); i++) {
            TraderView.Row r = rows.get(i);
            if (side == s && index == r.index()) g.fill(x + 1, y, x + pw - 1, y + ROW, SEL);
            g.renderItem(r.icon(), x + 3, y + 1);
            int col = r.ok() ? TEXT : DIM;
            g.drawString(font, cut(label(r), pw - 132), x + 22, y + 5, col, false);
            String cnt = String.valueOf(r.count());
            g.drawString(font, cnt, x + pw - 80 - font.width(cnt), y + 5, col, false);
            String price = r.ok() ? rub(r.price()) : cut(r.note(), 74);
            g.drawString(font, price, x + pw - 4 - font.width(price), y + 5, r.ok() ? ACCENT : DIM, false);
            y += ROW;
        }
        if (rows.isEmpty()) g.drawString(font, sell ? "пусто" : "склад пуст", x + 6, y + 5, DIM, false);
        if (rows.size() > rowsVisible()) g.drawString(font, "▼ колесо мыши", x + 4, listBottom() - 10, DIM, false);
    }

    private void renderContracts(GuiGraphics g) {
        int y0 = listTop(), y1 = listBottom();
        g.fill(left + 6, y0, left + w - 6, y1, PANEL);
        g.renderOutline(left + 6, y0, w - 12, y1 - y0, FRAME);
        g.drawString(font, "ЗАКАЗЫ И КОНТРАКТЫ — те же, что в КПК", left + 10, y0 + 3, ACCENT, false);
        int y = y0 + 14;
        List<TraderView.ContractRow> cs = view.contracts;
        if (cs.isEmpty()) g.drawString(font, "Заказов сейчас нет. Все контракты Зоны — в КПК, вкладка «Задания».", left + 10, y + 4, DIM, false);
        int n = rowsVisible() / 2;
        for (int i = scrollC; i < cs.size() && i < scrollC + n; i++) {
            List<FormattedCharSequence> lines = font.split(Component.literal("#" + cs.get(i).id() + " " + cs.get(i).text()), w - 200);
            for (int k = 0; k < Math.min(2, lines.size()); k++) g.drawString(font, lines.get(k), left + 10, y + 3 + k * 10, TEXT, false);
            y += ROW * 2;
        }
    }

    private void renderRepairs(GuiGraphics g) {
        int y0 = listTop(), y1 = listBottom();
        g.fill(left + 6, y0, left + w - 6, y1, PANEL);
        g.renderOutline(left + 6, y0, w - 12, y1 - y0, FRAME);
        g.drawString(font, "РЕМОНТ — детали со склада торговца и работа", left + 10, y0 + 3, ACCENT, false);
        int y = y0 + 14;
        if (view.repairs.isEmpty()) g.drawString(font, "Изношенных вещей нет.", left + 10, y + 4, DIM, false);
        for (int i = 0; i < view.repairs.size() && i < rowsVisible(); i++) {
            TraderView.Row r = view.repairs.get(i);
            g.renderItem(r.icon(), left + 10, y + 1);
            g.drawString(font, cut(label(r), w - 260), left + 30, y + 5, TEXT, false);
            g.drawString(font, "состояние " + r.count() + "%", left + w - 230, y + 5, DIM, false);
            String cost = r.ok() ? rub(r.price()) : r.note();
            g.drawString(font, cost, left + w - 96 - font.width(cost), y + 5, r.ok() ? ACCENT : DIM, false);
            y += ROW;
        }
    }

    /** Имя строки: «@…» — название предмета на языке игрока + хвост с сервера. */
    static String label(TraderView.Row r) {
        return r.name().startsWith(TraderWindow.ITEM_NAME) ? r.icon().getHoverName().getString() + r.name().substring(1) : r.name();
    }

    private String cut(String s, int px) {
        if (font.width(s) <= px) return s;
        return font.plainSubstrByWidth(s, Math.max(0, px - font.width("…"))) + "…";
    }

    /** 2100 → «2 100 р». */
    static String rub(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "−" : "") + b + " р";
    }
}
