package studypulse.forms;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.service.NotificationService;
import studypulse.service.ServiceException;
import studypulse.util.BadgeRenderer;
import studypulse.util.DateUtil;
import studypulse.util.RowColorRenderer;
import studypulse.util.SidebarUtil;
import studypulse.util.TableUtil;
import studypulse.util.UITheme;

/**
 * All notifications, newest first. Unread rows are bold; the type is a coloured badge.
 * cmbFilterType: index 0 = all types, then NotificationType order.
 */
public class NotificationFrame extends javax.swing.JFrame {

    private static final int COL_TYPE = 1;

    private final NotificationService notificationService = new NotificationService();
    private final List<Notification> shown = new ArrayList<Notification>();
    private DefaultTableModel tableModel;

    /**
     * Creates new form NotificationFrame
     */
    public NotificationFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.NOTIFICATIONS);
        applyTheme();
        setupTable();
        loadNotifications();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        UITheme.stylePrimaryButton(btnMarkRead);
        UITheme.styleDangerButton(btnDelete);
    }

    private void setupTable() {
        tableModel = TableUtil.install(tblNotifications, "Type", "Message", "Time", "Status");
        tblNotifications.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        RowColorRenderer.RowColorProvider rowColors = new RowColorRenderer.RowColorProvider() {
            @Override
            public Color rowColor(int modelRow) {
                return shown.get(modelRow).isRead() ? UITheme.SURFACE : UITheme.ROW_UNREAD;
            }
        };
        RowColorRenderer renderer = new RowColorRenderer(rowColors);
        renderer.setBoldRows(new RowColorRenderer.BoldRowProvider() {
            @Override
            public boolean isBold(int modelRow) {
                return !shown.get(modelRow).isRead();
            }
        });
        renderer.installOn(tblNotifications);
        tblNotifications.getColumnModel().getColumn(COL_TYPE).setCellRenderer(new BadgeRenderer(rowColors,
                new BadgeRenderer.BadgeColorProvider() {
                    @Override
                    public Color badgeColor(Object value) {
                        for (NotificationType t : NotificationType.values()) {
                            if (t.getLabel().equals(value)) {
                                return UITheme.notificationColor(t);
                            }
                        }
                        return UITheme.TEXT_MUTED;
                    }
                }));
        int[] widths = {50, 100, 560, 140, 80};
        TableColumnModel columns = tblNotifications.getColumnModel();
        for (int i = 1; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void loadNotifications() {
        int index = cmbFilterType.getSelectedIndex();
        NotificationType type = index <= 0 ? null : NotificationType.values()[index - 1];
        tableModel.setRowCount(0);
        shown.clear();
        try {
            for (Notification n : notificationService.getNotifications(type)) {
                shown.add(n);
                tableModel.addRow(new Object[]{n.getId(), n.getType().getLabel(), n.getMessage(),
                    DateUtil.formatRelative(n.getCreatedAt()), n.isRead() ? "Read" : "Unread"});
            }
            int unread = notificationService.countUnread();
            lblUnread.setText(unread + " unread");
            lblUnread.setForeground(unread > 0 ? UITheme.DANGER : UITheme.TEXT_MUTED);
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Ids of the selected rows (several rows can be selected). */
    private List<Integer> selectedIds() {
        List<Integer> ids = new ArrayList<Integer>();
        for (int viewRow : tblNotifications.getSelectedRows()) {
            ids.add(shown.get(tblNotifications.convertRowIndexToModel(viewRow)).getId());
        }
        return ids;
    }

    private void markRead() {
        List<Integer> ids = selectedIds();
        if (ids.isEmpty()) {
            showWarning("Please select one or more notifications first.");
            return;
        }
        try {
            notificationService.markRead(ids);
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
        loadNotifications();
    }

    private void markAllRead() {
        try {
            notificationService.markAllRead();
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
        loadNotifications();
    }

    private void delete() {
        List<Integer> ids = selectedIds();
        if (ids.isEmpty()) {
            showWarning("Please select one or more notifications first.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete " + (ids.size() == 1 ? "this notification" : ids.size() + " notifications") + "?",
                "Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            notificationService.delete(ids);
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
        loadNotifications();
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "StudyPulse", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlSidebar = new javax.swing.JPanel();
        lblTitle = new javax.swing.JLabel();
        lblUnread = new javax.swing.JLabel();
        cmbFilterType = new javax.swing.JComboBox<>();
        btnMarkRead = new javax.swing.JButton();
        btnMarkAllRead = new javax.swing.JButton();
        btnDelete = new javax.swing.JButton();
        scrNotifications = new javax.swing.JScrollPane();
        tblNotifications = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Notifications");

        pnlSidebar.setBackground(new java.awt.Color(34, 48, 63));

        javax.swing.GroupLayout pnlSidebarLayout = new javax.swing.GroupLayout(pnlSidebar);
        pnlSidebar.setLayout(pnlSidebarLayout);
        pnlSidebarLayout.setHorizontalGroup(
            pnlSidebarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 160, Short.MAX_VALUE)
        );
        pnlSidebarLayout.setVerticalGroup(
            pnlSidebarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 720, Short.MAX_VALUE)
        );

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTitle.setText("Notifications");

        lblUnread.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblUnread.setForeground(new java.awt.Color(214, 69, 69));
        lblUnread.setText("0 unread");

        cmbFilterType.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All types", "Workload", "Burnout", "Break", "Deadline", "Info" }));
        cmbFilterType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbFilterTypeActionPerformed(evt);
            }
        });

        btnMarkRead.setText("Mark as read");
        btnMarkRead.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMarkReadActionPerformed(evt);
            }
        });

        btnMarkAllRead.setText("Mark all as read");
        btnMarkAllRead.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMarkAllReadActionPerformed(evt);
            }
        });

        btnDelete.setText("Delete");
        btnDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteActionPerformed(evt);
            }
        });

        tblNotifications.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Type", "Message", "Time", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrNotifications.setViewportView(tblNotifications);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlSidebar, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblTitle)
                        .addGap(12, 12, 12)
                        .addComponent(lblUnread)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cmbFilterType, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnMarkRead)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnMarkAllRead)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDelete))
                    .addComponent(scrNotifications, javax.swing.GroupLayout.DEFAULT_SIZE, 1008, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTitle)
                    .addComponent(lblUnread)
                    .addComponent(cmbFilterType, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnMarkRead, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnMarkAllRead, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scrNotifications, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbFilterTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbFilterTypeActionPerformed
        loadNotifications();
    }//GEN-LAST:event_cmbFilterTypeActionPerformed

    private void btnMarkReadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMarkReadActionPerformed
        markRead();
    }//GEN-LAST:event_btnMarkReadActionPerformed

    private void btnMarkAllReadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMarkAllReadActionPerformed
        markAllRead();
    }//GEN-LAST:event_btnMarkAllReadActionPerformed

    private void btnDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteActionPerformed
        delete();
    }//GEN-LAST:event_btnDeleteActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnDelete;
    private javax.swing.JButton btnMarkAllRead;
    private javax.swing.JButton btnMarkRead;
    private javax.swing.JComboBox<String> cmbFilterType;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblUnread;
    private javax.swing.JPanel pnlSidebar;
    private javax.swing.JScrollPane scrNotifications;
    private javax.swing.JTable tblNotifications;
    // End of variables declaration//GEN-END:variables
}
