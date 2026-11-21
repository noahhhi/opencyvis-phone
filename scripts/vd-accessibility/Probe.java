package local.codex.vdaccess;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;

public final class Probe extends Instrumentation {
    private Bundle args;
    private final List<AccessibilityNodeInfo> matches = new ArrayList<>();
    private final StringBuilder summary = new StringBuilder();
    private int visited;
    private boolean incomplete;
    private boolean emailLabel;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            int display = Integer.parseInt(args.getString("display", "-1"));
            String pkg = args.getString("pkg", "");
            String click = args.getString("click");
            String email = args.getString("email");
            if (email != null && (click != null || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()))
                throw new IllegalArgumentException("Invalid email operation");
            if (display <= 0 || pkg.isEmpty() || "".equals(click))
                throw new IllegalArgumentException("Specify a virtual display and target package");
            UiAutomation ui = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            ui.setServiceInfo(info);
            for (int i = 0; i < 12; i++) {
                List<AccessibilityWindowInfo> windows = ui.getWindowsOnAllDisplays().get(display);
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        if (w.getDisplayId() != display || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                        AccessibilityNodeInfo root = w.getRoot();
                        if (root == null || !pkg.contentEquals(root.getPackageName())) continue;
                        if ((click != null || email != null) && !w.isActive() && !w.isFocused()) continue;
                        visit(root, click, 0);
                        break;
                    }
                }
                if (visited > 0) break;
                SystemClock.sleep(150);
            }
            if (visited == 0) throw new IllegalStateException("No matching app window on requested display");
            if (email != null) {
                if (incomplete || !emailLabel || matches.size() != 1) throw new IllegalStateException("Expected one labelled email field");
                Bundle value = new Bundle();
                value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, email);
                if (!matches.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value))
                    throw new IllegalStateException("Set text rejected");
                SystemClock.sleep(300);
                AccessibilityNodeInfo field = matches.get(0);
                if (!field.refresh() || !email.contentEquals(field.getText() == null ? "" : field.getText()))
                    throw new IllegalStateException("Value did not match after entry");
                summary.append("Email verified");
            } else if (click != null) {
                if (incomplete || matches.size() != 1)
                    throw new IllegalStateException("Incomplete tree or label not unique: " + matches.size());
                AccessibilityNodeInfo n = matches.get(0);
                while (n != null && !n.isClickable()) n = n.getParent();
                if (n == null || !n.refresh() || !n.isVisibleToUser() || !n.isEnabled()
                        || !n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    throw new IllegalStateException("Node did not accept click");
                summary.append("Click accepted");
            }
            result.putString("stream", summary.toString());
            finish(-1, result);
        } catch (Exception e) {
            result.putString("stream", "Error: " + e.getMessage());
            finish(0, result);
        }
    }

    private void visit(AccessibilityNodeInfo n, String click, int depth) {
        if (depth > 60 || visited >= 1200) { incomplete = true; return; }
        visited++;
        if (n.isPassword()) return;
        if (args.getString("email") != null) {
            if (n.isVisibleToUser()) {
                if ("电子邮件地址".contentEquals(n.getText() == null ? "" : n.getText())
                        || "Email address".equalsIgnoreCase(String.valueOf(n.getText()))) emailLabel = true;
                if (n.isEditable() && n.isEnabled()) matches.add(n);
            }
        } else if (n.isVisibleToUser() && !n.isEditable()) {
            String label = n.getText() == null ? "" : n.getText().toString();
            String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
            if (click != null) {
                if (click.equals(label) || click.equals(desc)) matches.add(n);
            } else if (!label.isEmpty() || !desc.isEmpty() || n.isClickable()) {
                Rect bounds = new Rect();
                n.getBoundsInScreen(bounds);
                summary.append('\n').append(bounds.toShortString()).append(" ")
                        .append(label.replace('\n',' ')).append(" ").append(desc.replace('\n',' '));
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            if (child != null) visit(child, click, depth + 1);
        }
    }
}
