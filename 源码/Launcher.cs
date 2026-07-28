using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace MTRLCDViewer
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            try
            {
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                Directory.SetCurrentDirectory(baseDir);

                string localJava = Path.Combine(baseDir, "runtime", "bin", "javaw.exe");
                string javaExe = File.Exists(localJava) ? localJava : "javaw.exe";

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = javaExe;
                psi.Arguments = "-jar \"MTR-LCD-Viewer.jar\"";
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.WorkingDirectory = baseDir;

                try
                {
                    Process.Start(psi);
                }
                catch
                {
                    // 尝试系统的 java.exe
                    psi.FileName = "java.exe";
                    Process.Start(psi);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "未检测到标准的 Java 运行环境 (JRE)。\n\n请先在系统中安装 Java 8 或以上版本，或确保同目录下包含 runtime 运行库。\n\n错误信息: " + ex.Message,
                    "MTR LCD Viewer 启动提示",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning
                );
            }
        }
    }
}
