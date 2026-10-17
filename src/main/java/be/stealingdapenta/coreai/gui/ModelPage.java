package be.stealingdapenta.coreai.gui;

final class ModelPage {

    static final int MODELS_PER_PAGE = 45;

    private ModelPage() {
    }

    static int count(int modelCount) {
        return Math.max(1, (int) (((long) modelCount + MODELS_PER_PAGE - 1) / MODELS_PER_PAGE));
    }

    static int modelIndex(int page, int rawSlot, int modelCount) {
        if (page < 0 || rawSlot < 0 || rawSlot >= MODELS_PER_PAGE || modelCount < 0) {
            return -1;
        }
        long index = (long) page * MODELS_PER_PAGE + rawSlot;
        return index < modelCount ? (int) index : -1;
    }
}
