package com.aicontrol.android.tool.impl.mobile;

import com.aicontrol.android.AiControlApplication;
import com.aicontrol.android.R;
import com.aicontrol.android.service.ClawAccessibilityService;
import com.aicontrol.android.tool.BaseTool;
import com.aicontrol.android.tool.ToolParameter;
import com.aicontrol.android.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TapTool extends BaseTool {

    @Override
    public String getName() {
        return "tap";
    }

    @Override
    public String getDisplayName() {
        return AiControlApplication.Companion.getInstance().getString(R.string.tool_name_tap);
    }

    @Override
    public String getDescriptionEN() {
        return "Tap at the specified screen position using percentage coordinates (0-100). " +
               "x_percent=0 is left edge, x_percent=100 is right edge. " +
               "y_percent=0 is top edge, y_percent=100 is bottom edge. " +
               "Example: tap(x_percent=50, y_percent=50) taps the center of the screen.";
    }

    @Override
    public String getDescriptionCN() {
        return "在屏幕指定位置点击，使用百分比坐标 (0-100)。x_percent=0 为左边缘，x_percent=100 为右边缘。y_percent=0 为上边缘，y_percent=100 为下边缘。例如：tap(x_percent=50, y_percent=50) 点击屏幕中心。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("x_percent", "integer", "X position as percentage (0-100, left to right)", true),
                new ToolParameter("y_percent", "integer", "Y position as percentage (0-100, top to bottom)", true)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        int xPercent = requireInt(params, "x_percent");
        int yPercent = requireInt(params, "y_percent");
        String boundsError = validatePercentCoordinates(xPercent, yPercent);
        if (boundsError != null) return ToolResult.error(boundsError);
        
        int x = xPercentToAbsolute(xPercent);
        int y = yPercentToAbsolute(yPercent);
        
        boolean success = service.performTap(x, y);
        return success ? ToolResult.success("Tapped at (" + xPercent + "%, " + yPercent + "%) → absolute (" + x + ", " + y + ")")
                : ToolResult.error("Failed to tap at (" + xPercent + "%, " + yPercent + "%)");
    }
}