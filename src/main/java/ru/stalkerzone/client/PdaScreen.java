package ru.stalkerzone.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.stalkerzone.net.Net;
import ru.stalkerzone.pda.PdaService;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран КПК. Вкладки: карта (подложка — снимок верхнего блока по мере исследования, владельцы точек, дороги, вы,
 * аномалии, логова, лаборатории, укрытия от выброса, проходы с ценой и опасностью, Сидорович, места заданий),
 * задания (контракты с кнопками), новости (журнал Зоны), отношения (репутация и группировки), лаборатории.
 */
public class PdaScreen extends Screen {
    private static final String[] TABS = {"Карта", "Задания", "Новости", "Отношения", "Лаборатории"};
    private static final int[] COLORS = {0xC08040, 0xD03030, 0x40C040, 0x909090, 0xF0F0F0, 0x8FAF3F, 0x70B8E0};
    private static final int BG = 0xF0141A14, PANEL = 0xF0202A20, FRAME = 0xFF5A6B4A, TEXT = 0xD8E0C8, DIM = 0x8C9A80, ACCENT = 0xE8C060;

    private PdaModel model;
    private int tab;
    private int scroll;
    private int left, top, w, h;
    private double zoom = 1;
    private int selected = -1;

    public PdaScreen(PdaModel model) {
        super(Component.literal("КПК"));
        this.model = model;
    }

    public void update(PdaModel m) {
        this.model = m;
        rebuild();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        w = Math.min(width - 20, 460);
        h = Math.min(height - 20, 270);
        left = (width - w) / 2;
        top = (height - h) / 2;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int tw = (w - 8) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> { tab = t; scroll = 0; rebuild(); })
                    .bounds(left + 4 + i * tw, top + 4, tw - 2, 16).build());
        }
        addRenderableWidget(Button.builder(Component.literal("↻"), b -> send(PdaService.REFRESH, 0)).bounds(left + w - 22, top + h - 20, 18, 16).build());
        if (tab == 1) contractButtons();
        if (tab == 4) addRenderableWidget(Button.builder(Component.literal("Сдать данные учёным"), b -> send(PdaService.LAB_DATA, 0))
                .bounds(left + 6, top + h - 20, 130, 16).build());
        if (tab == 0) {
            addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom = Math.min(6, zoom * 1.5)).bounds(left + w - 64, top + h - 20, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("−"), b -> zoom = Math.max(1, zoom / 1.5)).bounds(left + w - 44, top + h - 20, 18, 16).build());
        }
    }

    private void send(int action, int id) {
        Net.CHANNEL.sendToServer(new Net.PdaAction(action, id));
    }

    private static final int ROW = 36;

    private int contractsPerPage() { return Math.max(1, (h - 52) / ROW); }

    private void contractButtons() {
        List<PdaModel.ContractInfo> list = model.contracts;
        int per = contractsPerPage();
        int pages = Math.max(1, (list.size() + per - 1) / per);
        scroll = Math.max(0, Math.min(scroll, pages - 1));
        int y = top + 26;
        for (int k = scroll * per; k < Math.min(list.size(), (scroll + 1) * per); k++) {
            PdaModel.ContractInfo c = list.get(k);
            int bx = left + w - 78;
            int by = y;
            if ((c.actions() & PdaService.CAN_TAKE) != 0) addRenderableWidget(Button.builder(Component.literal("Взять"), b -> send(PdaService.TAKE, c.id())).bounds(bx, by, 72, 15).build());
            if ((c.actions() & PdaService.CAN_DELIVER) != 0) { addRenderableWidget(Button.builder(Component.literal("Сдать"), b -> send(PdaService.DELIVER, c.id())).bounds(bx, by, 72, 15).build()); by += 16; }
            if ((c.actions() & PdaService.CAN_ABANDON) != 0) addRenderableWidget(Button.builder(Component.literal("Отказаться"), b -> send(PdaService.ABANDON, c.id())).bounds(bx, by, 72, 15).build());
            if ((c.actions() & PdaService.CAN_CLAIM) != 0) addRenderableWidget(Button.builder(Component.literal("Получить"), b -> send(PdaService.CLAIM, c.id())).bounds(bx, by, 72, 15).build());
            y += ROW;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> { scroll--; rebuild(); }).bounds(left + w / 2 - 40, top + h - 20, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { scroll++; rebuild(); }).bounds(left + w / 2 + 22, top + h - 20, 18, 16).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + w + 2, top + h + 2, FRAME);
        g.fill(left, top, left + w, top + h, BG);
        g.fill(left + 4, top + 22, left + w - 4, top + h - 24, PANEL);
        switch (tab) {
            case 0 -> renderMap(g, mx, my);
            case 1 -> renderContracts(g);
            case 2 -> renderLines(g, model.events);
            case 3 -> renderFactions(g);
            default -> renderLabs(g);
        }
        String foot = "День " + model.day + "   " + model.player + "   " + model.rubles + " руб.";
        g.drawString(font, foot, left + (tab == 4 ? 142 : 8), top + h - 16, DIM);
        super.render(g, mx, my, pt);
    }

    // ---------------------------------------------------------------- карта

    private int mapX0, mapY0, mapW, mapH;
    private double cx, cz, scale;

    private int sx(double x) { return (int) Math.round(mapX0 + mapW / 2.0 + (x - cx) * scale); }
    private int sy(double z) { return (int) Math.round(mapY0 + mapH / 2.0 + (z - cz) * scale); }

    private void renderMap(GuiGraphics g, int mx, int my) {
        mapX0 = left + 6;
        mapY0 = top + 24;
        mapW = w - 150;
        mapH = h - 50;
        double spanX = model.maxX - model.minX, spanZ = model.maxZ - model.minZ;
        double base = Math.min(mapW / Math.max(1.0, spanX), mapH / Math.max(1.0, spanZ));
        scale = base * zoom;
        cx = zoom <= 1.01 ? (model.minX + model.maxX) / 2.0 : model.px;
        cz = zoom <= 1.01 ? (model.minZ + model.maxZ) / 2.0 : model.pz;
        g.enableScissor(mapX0, mapY0, mapX0 + mapW, mapY0 + mapH);
        g.fill(mapX0, mapY0, mapX0 + mapW, mapY0 + mapH, 0xFF1C241A);
        drawUnderlay(g);
        // граница Зоны
        box(g, sx(model.minX), sy(model.minZ), sx(model.maxX), sy(model.maxZ), 0xFF6A5A30);
        for (int[] e : model.edges) {
            PdaModel.NodeInfo a = model.node(e[0]), b = model.node(e[1]);
            if (a != null && b != null) line(g, sx(a.x()), sy(a.z()), sx(b.x()), sy(b.z()), 0xFF4E5A40);
        }
        for (PdaModel.Mark a : model.anomalies) {
            int col = a.level() >= 3 ? 0xFFFF4040 : a.level() >= 2 ? 0xFFFF9040 : 0xFFE0E040;
            g.fill(sx(a.x()) - 1, sy(a.z()) - 1, sx(a.x()) + 1, sy(a.z()) + 1, col);
        }
        for (PdaModel.Mark l : model.lairs) {
            int x = sx(l.x()), y = sy(l.z());
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF7A1010);
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFD04040);
        }
        for (PdaModel.LabInfo lab : model.labs) {
            int x = sx(lab.x()), y = sy(lab.z());
            g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF103050);
            g.fill(x - 2, y - 2, x + 3, y + 3, lab.open() ? 0xFF40A0FF : 0xFF406080);
        }
        for (PdaModel.ContractInfo c : model.contracts) {
            if (!c.mine() || (c.x() == 0 && c.z() == 0)) continue;
            int x = sx(c.x()), y = sy(c.z());
            box(g, x - 7, y - 7, x + 7, y + 7, 0xFFE8C060);
        }
        // укрытия от выброса (точки shelter уровня)
        PdaModel.Passage phover = null;
        for (PdaModel.Mark w : model.shelters) {
            int x = sx(w.x()), y = sy(w.z());
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF204060);
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF80C0F0);
        }
        // три прохода через насыпь: цвет по опасности, у платного — жёлтая рамка
        for (PdaModel.Passage p : model.passages) {
            int x = sx(p.x()), y = sy(p.z());
            int col = p.risk() >= 2 ? 0xFFFF5030 : p.risk() >= 1 ? 0xFFFFB040 : 0xFF40D060;
            g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF000000);
            g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000 | col);
            if (p.price() > 0) box(g, x - 6, y - 6, x + 7, y + 7, 0xFFE8C060);
            if (Math.abs(mx - x) <= 6 && Math.abs(my - y) <= 6) phover = p;
        }
        // торговцы точек: Сидорович и другие
        for (PdaModel.Mark t : model.traders) {
            int x = sx(t.x()), y = sy(t.z());
            g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFE8C060);
            if (zoom >= 1.2) g.drawString(font, t.name(), x + 5, y - 4, ACCENT, true);
        }
        PdaModel.NodeInfo hover = null;
        for (PdaModel.NodeInfo n : model.nodes) {
            int x = sx(n.x()), y = sy(n.z());
            int col = n.owner() >= 0 && n.owner() < COLORS.length ? COLORS[n.owner()] : 0x8A8A60;
            int r = n.flags().contains("h") ? 5 : 4;
            g.fill(x - r - 1, y - r - 1, x + r + 1, y + r + 1, 0xFF000000);
            g.fill(x - r, y - r, x + r, y + r, 0xFF000000 | col);
            if (n.flags().contains("w")) box(g, x - r - 3, y - r - 3, x + r + 3, y + r + 3, 0xFFFF3030);
            if (n.flags().contains("m")) box(g, x - r - 5, y - r - 5, x + r + 5, y + r + 5, 0xFF40FFFF);
            if (zoom >= 1.4 || n.flags().contains("h")) g.drawString(font, n.name(), x + r + 2, y - 4, TEXT, true);
            if (Math.abs(mx - x) <= r + 2 && Math.abs(my - y) <= r + 2) hover = n;
        }
        int px = sx(model.px), py = sy(model.pz);
        g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
        g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF20FF20);
        g.disableScissor();
        // легенда
        int lx = left + w - 140, ly = top + 26;
        g.drawString(font, "Владельцы:", lx, ly, ACCENT);
        ly += 11;
        for (PdaModel.FactionInfo f : model.factions) {
            if (f.members() <= 0 && f.nodes() <= 0) continue;   // пустые фракции среза в легенду не лезут
            g.fill(lx, ly + 1, lx + 7, ly + 8, 0xFF000000 | COLORS[Math.min(f.id(), COLORS.length - 1)]);
            g.drawString(font, f.name(), lx + 10, ly, TEXT);
            ly += 10;
        }
        ly += 4;
        legend(g, lx, ly, 0xFF20FF20, "вы");
        legend(g, lx, ly += 10, 0xFFFF9040, "аномалии");
        legend(g, lx, ly += 10, 0xFFD04040, "логова мутантов");
        legend(g, lx, ly += 10, 0xFF40A0FF, "лаборатории");
        legend(g, lx, ly += 10, 0xFFFF3030, "идёт штурм");
        legend(g, lx, ly += 10, 0xFF40FFFF, "встреча лидеров");
        legend(g, lx, ly += 10, 0xFFE8C060, "ваши задания");
        legend(g, lx, ly += 10, 0xFF40D060, "проходы (опасность)");
        legend(g, lx, ly += 10, 0xFF80C0F0, "укрытия от выброса");
        legend(g, lx, ly += 10, 0xFFE8C060, "Сидорович и торговцы");
        g.drawString(font, "Открыто клеток: " + PdaMapCache.known(), lx, ly + 12, DIM);
        g.drawString(font, "X " + model.px + "  Z " + model.pz, lx, ly + 22, DIM);
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.name()));
            PdaModel.FactionInfo f = model.faction(hover.owner());
            tip.add(Component.literal("Владелец: " + (f == null ? "ничья" : f.name())));
            if (hover.flags().contains("t")) tip.add(Component.literal("Есть торговец"));
            if (hover.flags().contains("l")) tip.add(Component.literal("Рядом логово мутантов"));
            if (hover.flags().contains("b")) tip.add(Component.literal("Подземная лаборатория"));
            if (hover.flags().contains("w")) tip.add(Component.literal("Идёт штурм"));
            int dist = (int) Math.hypot(hover.x() - model.px, hover.z() - model.pz);
            tip.add(Component.literal("До точки " + dist + " блоков"));
            g.renderComponentTooltip(font, tip, mx, my);
        } else if (phover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("Проход: " + phover.name()));
            PdaModel.FactionInfo f = model.faction(phover.owner());
            tip.add(Component.literal("Владелец: " + (f == null ? "ничья" : f.name())));
            tip.add(Component.literal(phover.price() > 0 ? "Пошлина " + phover.price() + " руб." : "Проход бесплатный"));
            tip.add(Component.literal("Опасность: " + (phover.risk() >= 2 ? "высокая" : phover.risk() >= 1 ? "средняя" : "низкая")));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    /** Подложка: цвет верхнего блока по сетке {@link PdaMapCache#STEP}, открытый игроком (туман войны). */
    private void drawUnderlay(GuiGraphics g) {
        if (mapW <= 0 || mapH <= 0 || scale <= 0) return;
        int step = 1;
        while ((mapW / step) * (mapH / step) > 9000) step++;
        for (int yy = 0; yy < mapH; yy += step) {
            for (int xx = 0; xx < mapW; xx += step) {
                double wx = cx + (xx + step / 2.0 - mapW / 2.0) / scale;
                double wz = cz + (yy + step / 2.0 - mapH / 2.0) / scale;
                int col = PdaMapCache.color(Math.floorDiv((int) Math.floor(wx), PdaMapCache.STEP),
                        Math.floorDiv((int) Math.floor(wz), PdaMapCache.STEP));
                if (col != 0) g.fill(mapX0 + xx, mapY0 + yy, mapX0 + xx + step, mapY0 + yy + step, col);
            }
        }
    }

    private void legend(GuiGraphics g, int x, int y, int col, String text) {
        g.fill(x + 1, y + 2, x + 6, y + 7, col);
        g.drawString(font, text, x + 10, y, TEXT);
    }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps > 2000) return;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps), y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    // ---------------------------------------------------------------- задания

    private void renderContracts(GuiGraphics g) {
        List<PdaModel.ContractInfo> list = model.contracts;
        if (list.isEmpty()) {
            g.drawString(font, "Заданий нет. Загляните позже: заказчики выдают их каждый день.", left + 10, top + 30, DIM);
            return;
        }
        int per = contractsPerPage();
        int y = top + 26;
        for (int k = scroll * per; k < Math.min(list.size(), (scroll + 1) * per); k++) {
            PdaModel.ContractInfo c = list.get(k);
            int col = c.mine() ? ACCENT : TEXT;
            List<FormattedCharSequence> lines = font.split(Component.literal(c.text()), w - 96);
            for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), left + 8, y + i * 10, col);
            g.fill(left + 6, y + ROW - 4, left + w - 6, y + ROW - 3, 0x40FFFFFF);
            y += ROW;
        }
        int per2 = Math.max(1, (list.size() + per - 1) / per);
        if (per2 > 1) g.drawCenteredString(font, (scroll + 1) + " / " + per2, left + w / 2, top + h - 16, DIM);
    }

    // ---------------------------------------------------------------- новости, группировки, лаборатории

    private List<FormattedCharSequence> wrapAll(List<String> src, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String s : src) out.addAll(font.split(Component.literal(s), width));
        return out;
    }

    private void renderLines(GuiGraphics g, List<String> src) {
        List<FormattedCharSequence> lines = wrapAll(src, w - 20);
        int visible = (h - 52) / 10;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        int y = top + 26;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            g.drawString(font, lines.get(i), left + 8, y, TEXT);
            y += 10;
        }
    }

    /** Вкладка «Отношения»: репутация игрока и отношения группировок среза. */
    private void renderFactions(GuiGraphics g) {
        List<String> out = new ArrayList<>();
        if (!model.rep.isEmpty()) {
            out.add("Репутация у вас:");
            for (PdaModel.Rep r : model.rep) {
                PdaModel.FactionInfo f = model.faction(r.faction());
                if (f == null) continue;
                String s = r.value() <= -40 ? "враг" : r.value() >= 40 ? "свой" : "нейтрально";
                out.add("   " + f.name() + ": " + r.value() + " (" + s + ")");
            }
            out.add("");
        }
        for (PdaModel.FactionInfo f : model.factions) {
            out.add(String.format("%s: %d чел. (ранены %d), точек %d, %s. Лидер: %s. Опыт %d, стресс %d, единство %d%%",
                    f.name(), f.members(), f.wounded(), f.nodes(), f.phase(), f.leader(), f.exp(), f.stress(), f.unity()));
            if (f.id() < model.hostility.size()) {
                int[] row = model.hostility.get(f.id());
                StringBuilder sb = new StringBuilder("   отношения: ");
                for (PdaModel.FactionInfo o : model.factions) {
                    if (o.id() == f.id() || o.id() >= row.length) continue;
                    int v = row[o.id()];
                    sb.append(o.name()).append(' ').append(v >= 60 ? "враг" : v >= 30 ? "напряжённо" : v <= 10 ? "мир" : "нейтрально").append("; ");
                }
                out.add(sb.toString());
            }
        }
        out.add("");
        out.add(model.meeting != null ? model.meeting : "Встреч лидеров пока не назначено");
        out.addAll(model.truces);
        renderLines(g, out);
    }

    private void renderLabs(GuiGraphics g) {
        List<String> out = new ArrayList<>();
        out.add(String.format("Цикл исследований %d. Выжигатель: %s, исследования %d%%, пси-защита %d%%. Монолит %s.", model.cycle,
                model.scorcherOff ? "отключён" : "работает", model.research, model.psiTech, model.monolith ? "пробудился" : "спит"));
        out.add("Победа Монолита (разгром лагеря учёных) уничтожает все данные: их придётся добывать заново.");
        out.add("");
        for (PdaModel.LabInfo l : model.labs) {
            out.add(String.format("%s (%s, вход X %d Z %d) %s сложность %d: скачано %d/%d, изучено %d/%d, премия %d руб. за часть%s",
                    l.name(), l.node(), l.x(), l.z(), "★★★★★".substring(0, Math.min(5, l.tier())), l.tier(), l.rec(), l.parts(), l.del(), l.parts(), l.reward(),
                    l.open() ? "" : " — закрыта, пока работает Выжигатель"));
            out.add("   " + l.lore() + (l.tier() >= 4 ? " Нужен переносной диск." : " Хватит флешки."));
        }
        renderLines(g, out);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 0) {
            zoom = Math.max(1, Math.min(6, zoom * (delta > 0 ? 1.25 : 0.8)));
            return true;
        }
        if (tab == 1) {
            scroll += delta > 0 ? -1 : 1;
            rebuild();
            return true;
        }
        scroll = Math.max(0, scroll + (delta > 0 ? -3 : 3));
        return true;
    }
}
