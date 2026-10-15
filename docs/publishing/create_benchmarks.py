from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import MultipleLocator

OUT = Path(__file__).parent
LABELS = ['Vanilla', 'Lithium', 'ECOpt', 'Accelerated Recoiling',
          'Lithium + ECOpt', 'Accelerated Recoiling + Lithium',
          'Accelerated Recoiling + ECOpt', 'All three']
COLORS = ['#8794a5', '#8794a5', '#147d70', '#8794a5', '#147d70', '#8794a5', '#147d70', '#147d70']
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 11,
                     'axes.spines.top': False, 'axes.spines.right': False,
                     'axes.spines.left': False, 'axes.edgecolor': '#b4bec9',
                     'text.color': '#172735', 'axes.labelcolor': '#172735',
                     'xtick.color': '#526273', 'ytick.color': '#172735',
                     'savefig.facecolor': '#ffffff'})

def panel(ax, indices, values, limit, step, title):
    ax.barh(range(len(indices)), [values[i] for i in indices],
            color=[COLORS[i] for i in indices], height=.6)
    ax.set_yticks(range(len(indices)), [LABELS[i] for i in indices])
    ax.invert_yaxis()
    ax.set_xlim(0, limit)
    ax.xaxis.set_major_locator(MultipleLocator(step))
    ax.set_xlabel('Reported workload time (ms) — lower is better', labelpad=12)
    ax.set_title(title, loc='left', fontsize=13, fontweight='bold', pad=14)
    ax.xaxis.grid(True, color='#e7ecf1')
    ax.set_axisbelow(True)
    ax.tick_params(axis='y', length=0, pad=10)
    for y,i in enumerate(indices):
        ax.text(values[i]+limit*.015, y, f'{values[i]:,} ms', va='center',
                fontsize=11, fontweight='bold' if i in [2,4,6,7] else 'normal')

def chart(filename, title, subtitle, values, detail, full_limit, full_step, zoom_limit, zoom_step):
    fig, axes = plt.subplots(2,1, figsize=(8.4,11.5), gridspec_kw={'height_ratios':[8,len(detail)]})
    fig.subplots_adjust(left=.41, right=.94, top=.84, bottom=.14, hspace=.65)
    fig.text(.035,.955,title,fontsize=20,fontweight='bold')
    fig.text(.035,.917,subtitle,fontsize=11,color='#526273')
    fig.text(.035,.883,'Green bars: ECOpt installed',fontsize=11,color='#147d70')
    panel(axes[0], list(range(8)), values, full_limit, full_step, 'All configurations · linear scale from zero')
    panel(axes[1], detail, values, zoom_limit, zoom_step, 'Optimized configurations · expanded scale')
    fig.text(.035,.03,'Source: docs/TECHNICAL.md; existing measurements, not a new benchmark run.\n'
             'Workload timings are not FPS or a general speedup guarantee.\n'
             'Hardware, sampling method and measurement variability are not provided.',
             fontsize=10,color='#526273',linespacing=1.6)
    fig.savefig(OUT/filename,dpi=100)
    plt.close(fig)

chart('benchmark-pigs.png', '4,000 pigs in a 3 × 3 enclosure',
      'Normal AI and gravity enabled; entity cramming damage disabled.',
      [1132,725,650,419,637,70,72,54], [5,6,7], 1350,300,90,20)
chart('benchmark-items.png', '4,000 stationary dropped items',
      'An extreme entity-query workload with stationary item entities.',
      [378,7,7,197,5,8,9,7], [1,2,4,5,6,7], 450,100,12,2)
print('Saved benchmark-pigs.png and benchmark-items.png')
